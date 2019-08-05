package freskog.effects.infra.rabbitmq.consumer

import java.io.IOException

import com.rabbitmq.client.{AMQP, Envelope, ShutdownSignalException, Consumer => RConsumer}
import freskog.effects.app.logger.Logger
import freskog.effects.infra.rabbitmq.admin.{ClientProvider, _}
import freskog.effects.infra.rabbitmq.observer._
import freskog.effects.infra.rabbitmq.topology._
import freskog.effects.infra.rabbitmq.{ClientFactory, Schedules}
import zio._
import zio.clock.Clock

object LiveConsumer {

  type ConsumerEnv = Clock with Logger with Observer with ClientProvider

  val maxInFlight: Int = 10

  def consume(topology: Declaration, queueName: String)(userFn: String => ZIO[Any, Nothing, Unit]): ZIO[ConsumerEnv, Nothing, Unit] =
    consumerFiber(topology, queueName) *> handleSome {
      case MessageReceived(payload, ack, nack) => userFn(payload).sandbox.foldCauseM(_ => nack, _ => ack)
    }

  def consumerFiber(topology: Declaration, name: String): ZIO[ConsumerEnv, Nothing, Fiber[Nothing, Nothing]] =
    ClientFactory
      .buildClientsFor(name)
      .use(initializeConsumerOn(topology, name).provide)
      .sandbox
      .retry(Schedules.loggingRetries(name))
      .option
      .forever
      .fork

  def initializeConsumerOn(topology: Declaration, queueName: String): ZIO[TopologyClient with AdminClient with Observer, IOException, Unit] =
    for {
      _       <- createTopology(topology)
      rts     <- ZIO.runtime[Observer]
      env     <- ZIO.environment[AdminClient with Observer]
      stopped <- Promise.make[IOException, Unit]
      _       <- basicQos(maxInFlight) tap notifyOf
      _ <- basicConsume(
            queueName,
            new RConsumer {
              override def handleConsumeOk(consumerTag: String): Unit =
                rts.unsafeRun(notifyOf(ConsumerStarted(consumerTag)))

              override def handleRecoverOk(consumerTag: String): Unit =
                rts.unsafeRun(notifyOf(ConsumerRecovered(consumerTag)))

              override def handleCancelOk(consumerTag: String): Unit =
                rts.unsafeRun(notifyOf(SubscriberCancelledByUser(consumerTag)) <* stopped.succeed(()))

              override def handleCancel(consumerTag: String): Unit =
                rts.unsafeRun(notifyOf(SubscriberCancelledByBroker(consumerTag)) <* stopped.fail(new IOException("cancelled by broker")))

              override def handleShutdownSignal(consumerTag: String, sig: ShutdownSignalException): Unit =
                rts.unsafeRun(notifyOf(ConsumerShutdownReceived(consumerTag, sig)) <* stopped.fail(new IOException(sig)))

              override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit =
                rts.unsafeRun(
                  notifyOf(
                    MessageReceived(
                      new String(body, "UTF-8"),
                      ack(envelope.getDeliveryTag).provide(env),
                      nack(envelope.getDeliveryTag, envelope.isRedeliver).provide(env)
                    )
                  )
                )
            }
          ) tap notifyOf
      _ <- stopped.await
    } yield ()

  def ack(tag: Long): ZIO[Observer with AdminClient, Nothing, Unit] =
    basicAck(tag, multiple = false).tap(notifyOf).unit.catchAll(handleError)

  def nack(tag: Long, redelivered: Boolean): ZIO[Observer with AdminClient, Nothing, Unit] =
    basicNack(tag, multiple = false, requeue = !redelivered).tap(notifyOf).unit.catchAll(handleError)

  def handleError(e: Throwable): ZIO[Observer, Nothing, Unit] =
    notifyOf(AmqpError(e))

}
