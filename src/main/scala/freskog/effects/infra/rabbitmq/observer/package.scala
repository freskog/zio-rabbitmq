package freskog.effects.infra.rabbitmq

import zio.ZIO

package object observer {

  val eventService: ZIO[Observer, Nothing, Observer.Service] =
    ZIO.access[Observer](_.observer)

  def notifyOf(event:AmqpEvent):ZIO[Observer, Nothing, Unit] =
    ZIO.accessM[Observer](_.observer.notifyOf(event))

  def listenTo[R](handler:AmqpEvent => ZIO[R, Nothing, Unit]): ZIO[R with Observer, Nothing, Unit] =
    eventService.flatMap( _ listenTo handler)

  def listenToSome[R](handler:PartialFunction[AmqpEvent, ZIO[R, Nothing, Unit]]): ZIO[R with Observer, Nothing, Unit] =
    eventService.flatMap( _ listenToSome handler)
}
