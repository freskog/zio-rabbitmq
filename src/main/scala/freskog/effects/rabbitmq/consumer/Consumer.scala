package freskog.effects.rabbitmq.consumer

import java.io.IOException
import java.util.concurrent.TimeUnit

import com.rabbitmq.client.{ Consumer => RConsumer, _ }
import freskog.effects.rabbitmq._
import freskog.effects.rabbitmq.admin._
import freskog.effects.rabbitmq.events._
import freskog.effects.rabbitmq.topology._
import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration

trait Consumer extends Serializable {
  val consumer: Consumer.Service[Any]
}

object Consumer extends Serializable {

  trait Service[R] extends Serializable {
    def pollingConsumerOn[R1, E](queueName: String, topology: Declaration)(userFunction: String => ZIO[R1, E, Unit]): ZIO[R1, E, Unit]
    def callbackConsumerOn[R1, E](queueName: String, topology: Declaration)(userFunction: String => ZIO[R1, E, Unit]): ZIO[R1, E, Unit]
  }

  type ConsumerEnv = AdminClient with TopologyClient with Events with Clock

  def consumerLiveEnv(cf: ConnectionFactory, name: String): ZManaged[Any, IOException, ConsumerEnv] =
    for {
      adminEnv    <- AdminClient.makeLiveAdminClient(cf, name)
      eventsEnv   <- Events.makeEvents.toManaged_
      topologyEnv = TopologyClient.makeLiveTopologyClientFrom(adminEnv, eventsEnv)
    } yield new AdminClient with TopologyClient with Events with Clock.Live {
      override val adminClient: AdminClient.Service[Any]       = adminEnv.adminClient
      override val topologyClient: TopologyClient.Service[Any] = topologyEnv.topologyClient
      override val events: Events.Service[Any]                 = eventsEnv.events
    }

  val maxInFlight: Int = 10

  val messageQueue: UIO[Queue[AmqpMessage]] =
    Queue.bounded[AmqpMessage](maxInFlight)

  val every500ms: Schedule[Any, Int] =
    Schedule.spaced(Duration(500, TimeUnit.MILLISECONDS))

  val untilNonEmpty: Schedule[Option[MessageReceived], MessageReceived] =
    every500ms *> Schedule.doUntil[Option[MessageReceived]](_.nonEmpty).map(_.get)

  trait Live extends Consumer { env =>

    val connectionFactory: ConnectionFactory

    override val consumer: Service[Any] =
      new Service[Any] {
        override def callbackConsumerOn[R, E](queueName: String, decl: Declaration)(userFun: String => ZIO[R, E, Unit]): ZIO[R, E, Unit] =
          consumeOn(connectionFactory, queueName, decl, initializeConsumerOn(queueName), userFun)

        override def pollingConsumerOn[R, E](queueName: String, decl: Declaration)(userFun: String => ZIO[R, E, Unit]): ZIO[R, E, Unit] =
          consumeOn(connectionFactory, queueName, decl, pollForNewMessages(queueName), userFun)
      }
  }

  def consumeOn[R, E](
    cf: ConnectionFactory,
    queueName: String,
    decl: Declaration,
    strategy: ZIO[ConsumerEnv, IOException, Unit],
    userFun: String => ZIO[R, E, Unit]
  ): ZIO[R, E, Unit] =
    for {
      messages     <- messageQueue
      init         = createTopology(decl)
      logger       = subscribe(log(s"Consumer on '$queueName'"))
      brokerEvents = subscribeSome(handleEventFromBroker(messages))
      consumer     = brokerEvents *> logger *> init *> strategy
      _            <- consumerFiber(cf, queueName, consumer)
      _            <- consumeWithUserFunction(userFun, messages)
    } yield ()

  def consumeWithUserFunction[R, E](userFunction: String => ZIO[R, E, Unit], messages: Queue[AmqpMessage]): ZIO[R, E, Nothing] =
    messages.take.flatMap(msg => userFunction(msg.body).sandbox.foldCauseM(_ => msg.nack, _ => msg.ack)).forever

  def ack(tag: Long): ZIO[Events with AdminClient, Nothing, Unit] =
    basicAck(tag, multiple = false).tap(publish).catchAll(printErrMsg).unit

  def nack(tag: Long, redelivered: Boolean): ZIO[Events with AdminClient, Nothing, Unit] =
    basicNack(tag, multiple = false, requeue = !redelivered).tap(publish).catchAll(printErrMsg).unit

  def log(prefix: String)(event: AmqpEvent): UIO[Unit] =
    console.putStrLn(s"$prefix - $event").provide(console.Console.Live)

  def handleEventFromBroker(messages: Queue[AmqpMessage]): PartialFunction[AmqpEvent, ZIO[Events with AdminClient, Nothing, Unit]] = {
    case SubscriberCancelledByUser(_, stopped)   => stopped.succeed(()).unit
    case SubscriberCancelledByBroker(_, stopped) => stopped.succeed(()).unit
    case ConsumerShutdownReceived(_, _, stopped) => stopped.succeed(()).unit
    case MessageReceived(seqNo, redelivered, payload) =>
      for {
        env <- ZIO.environment[Events with AdminClient]
        msg = AmqpMessage(payload, ack(seqNo).provide(env), nack(seqNo, redelivered).provide(env))
        _   <- messages.offer(msg)
      } yield ()
  }

  def consumerFiber[A](cf: ConnectionFactory, name: String, consumer: ZIO[ConsumerEnv, IOException, A]): UIO[Fiber[Nothing, Nothing]] =
    consumerLiveEnv(cf, name).use(consumer.provide).sandbox.retry(Schedules.restartFiber(name)).provide(Clock.Live).option.forever.fork

  def pollForNewMessages(queueName: String): ZIO[AdminClient with Events with Clock, IOException, Nothing] =
    basicGet(queueName).repeat(untilNonEmpty).tap(publish).forever

  def initializeConsumerOn(queueName: String): ZIO[AdminClient with Events, IOException, Unit] =
    for {
      rts            <- ZIO.runtime[Events]
      stopped        <- Promise.make[Nothing, Unit]
      _              <- basicQos(maxInFlight) tap publish
      rabbitConsumer <- makeRabbitConsumer(rts, stopped)
      _              <- basicConsume(queueName, rabbitConsumer) tap publish
      _              <- stopped.await
    } yield ()

  def makeRabbitConsumer(rts: Runtime[Events], stopped: Promise[Nothing, Unit]): UIO[RConsumer] =
    ZIO.effectTotal {
      new RConsumer {
        override def handleConsumeOk(consumerTag: String): Unit =
          rts.unsafeRun(publish(ConsumerStarted(consumerTag)))

        override def handleRecoverOk(consumerTag: String): Unit =
          rts.unsafeRun(publish(ConsumerRecovered(consumerTag)))

        override def handleCancelOk(consumerTag: String): Unit =
          rts.unsafeRun(publish(SubscriberCancelledByUser(consumerTag, stopped)))

        override def handleCancel(consumerTag: String): Unit =
          rts.unsafeRun(publish(SubscriberCancelledByBroker(consumerTag, stopped)))

        override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
          rts.unsafeRun(publish(ConsumerShutdownReceived(consumerTag, sig, stopped)))

        override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit =
          rts.unsafeRun(publish(MessageReceived(envelope.getDeliveryTag, envelope.isRedeliver, new String(body, "UTF-8"))))
      }
    }
}
