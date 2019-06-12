package freskog.effects.rabbitmq.consumer

import java.io.IOException
import java.util.concurrent.TimeUnit

import com.rabbitmq.client.{ AMQP, Channel, Envelope, GetResponse, ShutdownSignalException, Consumer => RConsumer }
import freskog.effects.rabbitmq._
import freskog.effects.rabbitmq.admin._
import freskog.effects.rabbitmq.topology._
import scalaz.zio._
import scalaz.zio.blocking.Blocking
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration

trait Consumer extends Serializable {
  val consumer: Consumer.Service[Any]
}

object Consumer extends Serializable {
  self =>

  trait Service[R] extends Serializable {
    def pollingConsumerOn[R1, E](queueName: String, topology: Declaration)(userFunction: String => ZIO[R1, E, Unit]): ZIO[R1, E, Unit]
    def callbackConsumerOn[R1, E](queueName: String, topology: Declaration)(userFunction: String => ZIO[R1, E, Unit]): ZIO[R1, E, Unit]
  }

  trait Live extends Consumer with TopologyClient with AdminClient with Blocking.Live with Clock.Live {
    env =>
    override val consumer: Service[Any] =
      new Service[Any] {
        override def callbackConsumerOn[R, E](queueName: String, topology: Declaration)(
          userFun: String => ZIO[R, E, Unit]
        ): ZIO[R, E, Unit] =
          self.callbackConsumerOn[R, E](queueName, topology, env)(userFun)

        override def pollingConsumerOn[R, E](queueName: String, topology: Declaration)(
          userFun: String => ZIO[R, E, Unit]
        ): ZIO[R, E, Unit] =
          self.pollingConsumerOn[R, E](queueName, topology, env)(userFun)
      }
  }

  type ConsumerEnv = AdminClient with TopologyClient with Blocking with Clock

  val maxInFlight = 10

  val consumerQueue: UIO[Queue[AmqpMessage]] =
    Queue.bounded[AmqpMessage](maxInFlight)

  val every500ms: Schedule[Any, Int] =
    Schedule.spaced(Duration(500, TimeUnit.MILLISECONDS))

  val untilNonNull: Schedule[GetResponse, GetResponse] =
    every500ms *> Schedule.doUntil[Option[GetResponse]](_.nonEmpty).map(_.get).contramap(Option(_))

  def callbackConsumerOn[R, E](queueName: String, topology: Declaration, env: ConsumerEnv)(
    userFunction: String => ZIO[R, E, Unit]
  ): ZIO[R, E, Unit] =
    for {
      queue <- consumerQueue
      _     <- callbackConsumerFiber(queueName, topology, queue).provide(env).fork
      _     <- (queue.take >>= consumeWithUserFunction[R, E](env, userFunction)).forever
    } yield ()

  def pollingConsumerOn[R, E](queueName: String, topology: Declaration, env: ConsumerEnv)(
    userFunction: String => ZIO[R, E, Unit]
  ): ZIO[R, E, Unit] =
    for {
      queue <- consumerQueue
      _     <- pollingConsumerFiber(queueName, topology, queue).provide(env).fork
      _     <- (queue.take >>= consumeWithUserFunction[R, E](env, userFunction)).forever
    } yield ()

  def consumeWithUserFunction[R, E](client: AdminClient, userFunction: String => ZIO[R, E, Unit])(msg: AmqpMessage): ZIO[R, E, Unit] =
    userFunction(msg.body).sandbox.foldCauseM(
      _ => basicNack(msg.fromChannel, msg.tag, multiple = false, requeue = true).provide(client).catchAll(printErrMsg),
      _ => basicAck(msg.fromChannel, msg.tag, multiple = false).provide(client).catchAll(printErrMsg)
    )

  def convertToMessage(fromChannel: Channel, resp: GetResponse): AmqpMessage =
    AmqpMessage(new String(resp.getBody, "UTF-8"), resp.getEnvelope.getDeliveryTag, fromChannel)

  def pollingConsumerFiber[E](queueName: String, topology: Declaration, queue: Queue[AmqpMessage]): ZIO[ConsumerEnv, Nothing, Unit] =
    createManagedChannel(queueName).use { channel =>
      createTopology(topology) *>
        (basicGet(channel, queueName).map(convertToMessage(channel, _)) >>= queue.offer).forever
    }.sandbox.retry(Schedules.restartFiber(queueName)).option.unit

  def callbackConsumerFiber(queueName: String, topology: Declaration, queue: Queue[AmqpMessage]): ZIO[ConsumerEnv, Nothing, Unit] =
    createManagedChannel(queueName).use { channel =>
      for {
        rts    <- ZIO.runtime[Any]
        status <- Promise.make[IOException, Unit]
        _      <- createTopology(topology)
        _      <- basicQos(channel, prefetchSize = 0, prefetchCount = maxInFlight, global = true)
        _      <- basicConsume(channel, queueName, autoAck = false, makeRabbitConsumer(channel, rts, status, queue))
        _      <- status.await
      } yield ()
    }.sandbox.retry(Schedules.restartFiber(queueName)).option.unit

  def makeRabbitConsumer(
    channel: Channel,
    rts: Runtime[Any],
    status: Promise[IOException, Unit],
    queue: Queue[AmqpMessage]
  ): RConsumer =
    new RConsumer {
      override def handleConsumeOk(consumerTag: String): Unit = ()
      override def handleRecoverOk(consumerTag: String): Unit = ()
      override def handleCancelOk(consumerTag: String): Unit =
        rts.unsafeRun(status.succeed(()))

      override def handleCancel(consumerTag: String): Unit =
        rts.unsafeRun(status.fail(new IOException(new IllegalStateException("Broker cancelled us!"))))

      override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
        rts.unsafeRun(status.fail(new IOException(sig)))

      override def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: AMQP.BasicProperties,
        body: Array[Byte]
      ): Unit =
        rts.unsafeRun(queue.offer(AmqpMessage(new String(body, "UTF-8"), envelope.getDeliveryTag, channel)))

    }

}
