package freskog.effects.infra.rabbitmq.consumer

import freskog.effects.app.logger.Logger
import freskog.effects.infra.rabbitmq.AmqpLogger
import freskog.effects.infra.rabbitmq.admin.ClientProvider
import freskog.effects.infra.rabbitmq.observer.Observer
import freskog.effects.infra.rabbitmq.topology._
import zio._
import zio.clock.Clock

trait Consumer extends Serializable {
  val consumer: Consumer.Service[Any]
}

object Consumer extends Serializable {

  trait Service[R] extends Serializable {
    def consume(userFunction: String => UIO[Unit]): ZIO[R, Nothing, Unit]
  }

  def createConsumer(topology: Declaration, queueName: String): ZIO[Clock with Logger with ClientProvider, Nothing, Consumer] =
    for {
      outsideEnv  <- ZIO.environment[Clock with Logger with ClientProvider]
      observerEnv <- Observer.makeObserver
    } yield new Consumer {
      override val consumer: Service[Any] =
        (userFunction: String => UIO[Unit]) =>
          (AmqpLogger.logEvents(s"Q: $queueName") *> LiveConsumer.consume(topology, queueName)(userFunction))
            .provide(
              new Clock with Logger with Observer with ClientProvider {
                override val clock: Clock.Service[Any]                         = outsideEnv.clock
                override val logger: Logger.Service[Any]                       = outsideEnv.logger
                override val observer: Observer.Service[Any]                   = observerEnv.observer
                override val adminClientProvider: ClientProvider.Provider[Any] = outsideEnv.adminClientProvider
              }
            )
    }

}
