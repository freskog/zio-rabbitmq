package freskog.effects.infra.rabbitmq.publisher

import java.io.IOException

import freskog.effects.app.logger.Logger
import freskog.effects.infra.rabbitmq.AmqpLogger
import freskog.effects.infra.rabbitmq.admin.ClientProvider
import freskog.effects.infra.rabbitmq.observer.Observer
import freskog.effects.infra.rabbitmq.topology._
import zio._
import zio.clock.Clock

trait Publisher extends Serializable {
  val publisher: Publisher.Service[Any]
}

object Publisher extends Serializable {

  trait Service[R] extends Serializable {
    def publishMessage(payload: String): ZIO[R, IOException, Unit]
  }

  def createPublisher(topology: Declaration, exchange: String): ZIO[ClientProvider with Logger with Clock, Nothing, Publisher] =
    for {
      observerEnv <- Observer.makeObserver
      outsideEnv  <- ZIO.environment[ClientProvider with Logger with Clock]
      pfn <- (AmqpLogger.logEvents(s"E: $exchange") *> LivePublisher.publishMessage(topology, exchange))
              .provide(
                new Observer with ClientProvider with Logger with Clock {
                  override val observer: Observer.Service[Any]                   = observerEnv.observer
                  override val adminClientProvider: ClientProvider.Provider[Any] = outsideEnv.adminClientProvider
                  override val logger: Logger.Service[Any]                       = outsideEnv.logger
                  override val clock: Clock.Service[Any]                         = outsideEnv.clock
                }
              )
    } yield new Publisher { override val publisher: Service[Any] = (payload: String) => pfn(payload) }

}
