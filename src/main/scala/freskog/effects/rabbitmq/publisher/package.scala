package freskog.effects.rabbitmq

import java.io.IOException

import freskog.effects.rabbitmq.topology.Declaration
import scalaz.zio._

package object publisher {

  val publisherService: ZIO[Publisher, Nothing, Publisher.Service[Any]] =
    ZIO.access[Publisher](_.publisher)

  def publishTo(exchange: String, topology: Declaration): ZIO[Publisher, Nothing, String => UIO[Unit]] =
    ZIO.accessM[Publisher](_.publisher.publishTo(exchange, topology))

  def publishConfirmsTo(exchange: String, topology: Declaration): ZIO[Publisher, Nothing, String => ZIO[Any, IOException, Unit]] =
    ZIO.accessM[Publisher](_.publisher.publishConfirmsTo(exchange, topology))

}
