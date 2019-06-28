package freskog.effects.infra.rabbitmq

import scalaz.zio.ZIO

package object events {

  val eventService: ZIO[Events, Nothing, Events.Service] =
    ZIO.access[Events](_.events)

  def publish(event:AmqpEvent):ZIO[Events, Nothing, Unit] =
    ZIO.accessM[Events](_.events.publish(event))

  def subscribe[R](handler:AmqpEvent => ZIO[R, Nothing, Unit]): ZIO[R with Events, Nothing, Unit] =
    eventService.flatMap( _ subscribe handler)

  def subscribeSome[R](handler:PartialFunction[AmqpEvent, ZIO[R, Nothing, Unit]]): ZIO[R with Events, Nothing, Unit] =
    eventService.flatMap( _ subscribeSome handler)
}
