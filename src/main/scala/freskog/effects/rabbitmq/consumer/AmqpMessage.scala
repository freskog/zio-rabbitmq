package freskog.effects.rabbitmq.consumer

import scalaz.zio.ZIO

case class AmqpMessage(body:String, ack:ZIO[Any,Nothing,Unit], nack:ZIO[Any,Nothing,Unit])