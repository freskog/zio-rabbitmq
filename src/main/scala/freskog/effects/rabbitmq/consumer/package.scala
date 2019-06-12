package freskog.effects.rabbitmq

import freskog.effects.rabbitmq.topology.Declaration
import scalaz.zio.ZIO

package object consumer {

  val consumerService: ZIO[Consumer, Nothing, Consumer.Service[Any]] =
    ZIO.access(_.consumer)

  def callbackConsumerOn[R, E](queueName: String, topology:Declaration)(userFunction: String => ZIO[R, E, Unit]): ZIO[R with Consumer, E, Unit] =
    ZIO.access[Consumer](_.consumer.callbackConsumerOn[R, E](queueName, topology)(userFunction)).flatten

  def pollingConsumerOn[R, E](queueName: String, topology:Declaration)(userFunction: String => ZIO[R, E, Unit]): ZIO[R with Consumer, E, Unit] =
    ZIO.access[Consumer](_.consumer.pollingConsumerOn[R, E](queueName, topology)(userFunction)).flatten
}
