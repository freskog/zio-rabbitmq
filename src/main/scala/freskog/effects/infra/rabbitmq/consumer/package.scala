package freskog.effects.infra.rabbitmq

import scalaz.zio.ZIO

package object consumer {

  val consumerService: ZIO[Consumer, Nothing, Consumer.Service] =
    ZIO.access(_.consumer)

  def consumeUsing[R, E](userFunction: String => ZIO[R, E, Unit]): ZIO[R with Consumer, E, Unit] =
    ZIO.access[Consumer](_.consumer.consumeUsing(userFunction)).flatten

}
