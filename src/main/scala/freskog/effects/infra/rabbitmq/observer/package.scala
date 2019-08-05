package freskog.effects.infra.rabbitmq

import zio.{Fiber, ZIO}

package object observer extends Observer.Service[Observer] {

  val observerService: ZIO[Observer, Nothing, Observer.Service[Any]] =
    ZIO.access[Observer](_.observer)

  def notifyOf(event:AmqpEvent):ZIO[Observer, Nothing, Unit] =
    observerService >>= (_ notifyOf event)

  override def handle[R1, E](handler: AmqpEvent => ZIO[R1, E, Unit]): ZIO[Observer with R1, E, Nothing] =
    observerService >>= (_ handle handler)

  override def handleSome[R1, E](handler: PartialFunction[AmqpEvent, ZIO[R1, E, Unit]]): ZIO[Observer with R1, E, Nothing] =
    observerService >>= (_ handleSome handler)
}
