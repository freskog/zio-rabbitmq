package freskog.effects.infra.rabbitmq

import java.io.IOException

import scalaz.zio._

package object publisher {

  val publisherService: ZIO[Publisher, Nothing, Publisher.Service[Any]] =
    ZIO.access[Publisher](_.publisher)

  def publishMessage(msg: String): ZIO[Publisher, IOException, Unit] =
    publisherService.flatMap(_.publishMessage(msg))

}
