package freskog.effects.infra.rabbitmq

import java.io.IOException

import zio._

package object publisher {

  val publisherService: ZIO[Publisher, Nothing, Publisher.Service] =
    ZIO.access[Publisher](_.publisher)

  def publishMessage(msg: String): ZIO[Publisher, IOException, Unit] =
    ZIO.accessM[Publisher](_.publisher.publishMessage(msg))

}
