package freskog.effects.infra.rabbitmq

import zio.{UIO, ZIO}

package object consumer extends Consumer.Service[Consumer] {

  val consumerService: ZIO[Consumer, Nothing, Consumer.Service[Any]] =
    ZIO.access(_.consumer)

  def consume(userFunction: String => UIO[Unit]): ZIO[Consumer, Nothing, Unit] = {
    consumerService >>= (_ consume userFunction)
  }

}
