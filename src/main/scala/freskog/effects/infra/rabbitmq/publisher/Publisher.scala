package freskog.effects.infra.rabbitmq.publisher

import java.io.IOException

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.infra.rabbitmq.topology._
import zio._

trait Publisher extends Serializable {
  val publisher: Publisher.Service
}

object Publisher extends Serializable {

  trait Service extends Serializable {
    def publishMessage(payload:String): IO[IOException, Unit]
  }

  def makePublisherWithConfirms(cf:ConnectionFactory, exchange: String, topology: Declaration): UIO[Publisher] =
    LivePublisher.publishConfirmsTo(cf, exchange, topology)
      .map (publishFn => new Publisher { override val publisher: Service = (payload: String) => publishFn(payload) })

  def makePublisherWithoutConfirms(cf:ConnectionFactory, exchange: String, topology:Declaration): UIO[Publisher] =
    LivePublisher.publishTo(cf, exchange, topology)
      .map (publishFn => new Publisher { override val publisher: Service = (payload: String) => publishFn(payload) })
}
