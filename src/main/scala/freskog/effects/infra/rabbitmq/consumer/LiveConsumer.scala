package freskog.effects.infra.rabbitmq.consumer

import java.io.IOException

import com.rabbitmq.client.{ AMQP, ConnectionFactory, Envelope, ShutdownSignalException, Consumer => RConsumer }
import freskog.effects.infra.logger._
import freskog.effects.infra.rabbitmq._
import freskog.effects.infra.rabbitmq.admin._
import freskog.effects.infra.rabbitmq.events._
import freskog.effects.infra.rabbitmq.topology.{ createTopology, Declaration, TopologyClient }
import scalaz.zio.clock.Clock
import scalaz.zio.{ Fiber, Promise, Queue, Runtime, UIO, ZIO, ZManaged }

object LiveConsumer {

  type ConsumerEnv = AdminClient with TopologyClient with Events with Clock with Logger

  def liveConsumerEnv(cf: ConnectionFactory, name: String): ZManaged[Any, IOException, ConsumerEnv] =
    for {
      adminEnv    <- AdminClient.makeLiveAdminClient(cf, name)
      eventsEnv   <- Events.makeEvents.toManaged_
      topologyEnv = TopologyClient.makeLiveTopologyClientFrom(adminEnv, eventsEnv)
      loggerEnv   <- Logger.makeLogger("Consumer").toManaged_
    } yield new AdminClient with TopologyClient with Events with Logger with Clock.Live {
      override val adminClient: AdminClient.Service       = adminEnv.adminClient
      override val topologyClient: TopologyClient.Service = topologyEnv.topologyClient
      override val events: Events.Service                 = eventsEnv.events
      override val logger: Logger.Service                 = loggerEnv.logger
    }

  val retryEnv: ZIO[Any, Nothing, Logger with Clock.Live] =
    Logger
      .makeLogger("Consumer")
      .map(
        env =>
          new Logger with Clock.Live {
            override val logger: Logger.Service = env.logger
          }
      )

  val maxInFlight: Int = 10

  case class AmqpMessage(body: String, ack: ZIO[Any, Nothing, Unit], nack: ZIO[Any, Nothing, Unit])

  val messageQueue: UIO[Queue[AmqpMessage]] = Queue.bounded[AmqpMessage](maxInFlight)

  def consumeWith[R, E](
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

  def ack(tag: Long): ZIO[Events with AdminClient with Logger, Nothing, Unit] =
    basicAck(tag, multiple = false).tap(publish).catchAll(throwable).unit

  def nack(tag: Long, redelivered: Boolean): ZIO[Events with AdminClient with Logger, Nothing, Unit] =
    basicNack(tag, multiple = false, requeue = !redelivered).tap(publish).catchAll(throwable).unit

  def log(prefix: String)(event: AmqpEvent): ZIO[Logger, Nothing, Unit] = event match {
    case SubscriberCancelledByUser(_, _)   => warn(s"$prefix - $event")
    case SubscriberCancelledByBroker(_, _) => error(s"$prefix - $event")
    case ConsumerShutdownReceived(_, _, _) => error(s"$prefix - $event")
    case PublisherShutdownReceived(_, _)   => error(s"$prefix - $event")
    case _: AmqpEvent                      => debug(s"$prefix - $event")
  }

  def handleEventFromBroker(messages: Queue[AmqpMessage]): PartialFunction[AmqpEvent, ZIO[Events with AdminClient with Logger, Nothing, Unit]] = {
    case SubscriberCancelledByUser(_, stopped)   => stopped.succeed(()).unit
    case SubscriberCancelledByBroker(_, stopped) => stopped.succeed(()).unit
    case ConsumerShutdownReceived(_, _, stopped) => stopped.succeed(()).unit
    case MessageReceived(seqNo, redelivered, payload) =>
      for {
        env <- ZIO.environment[Events with AdminClient with Logger]
        msg = AmqpMessage(payload, ack(seqNo).provide(env), nack(seqNo, redelivered).provide(env))
        _   <- messages.offer(msg)
      } yield ()
  }

  def consumerFiber[A](cf: ConnectionFactory, name: String, consumer: ZIO[ConsumerEnv, IOException, A]): UIO[Fiber[Nothing, Nothing]] =
    retryEnv >>= (env => liveConsumerEnv(cf, name).use(consumer.provide).sandbox.retry(Schedules.restartFiber(name)).provide(env).option.forever.fork)

  def pollForNewMessages(queueName: String): ZIO[AdminClient with Events with Clock, IOException, Nothing] =
    basicGet(queueName).repeat(Schedules.untilNonEmpty).tap(publish).forever

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
