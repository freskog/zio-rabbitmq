package freskog.effects.infra.rabbitmq.events

import scalaz.zio._

trait Events extends Serializable {
  val events:Events.Service[Any]
}

object Events extends Serializable {

  trait Service[R] {
    def publish(e:AmqpEvent):ZIO[R, Nothing, Unit]
    def subscribe[R1 <: R](handler: AmqpEvent => ZIO[R1,Nothing,Unit]):ZIO[R1, Nothing, Unit]
    def subscribeSome[R1 <: R](handler: PartialFunction[AmqpEvent, ZIO[R1, Nothing, Unit]]):ZIO[R1,Nothing, Unit]
  }

  def makeEvents:UIO[Events] =
    Ref.make[List[Queue[AmqpEvent]]](Nil) >>= { ref =>
      ZIO.effectTotal(new Live { override val handlers: Ref[List[Queue[AmqpEvent]]] = ref })
    }

  trait Live extends Events {

    val handlers:Ref[List[Queue[AmqpEvent]]]

    override val events: Service[Any] =
      new Service[Any] {
        override def publish(e: AmqpEvent): ZIO[Any, Nothing, Unit] =
         handlers.get.flatMap(ZIO.foreach_(_)(_ offer e))

        override def subscribeSome[R](pf:PartialFunction[AmqpEvent, ZIO[R, Nothing, Unit]]):ZIO[R, Nothing, Unit] =
          subscribe(pf.applyOrElse(_, (_:AmqpEvent) => ZIO.unit))

        override def subscribe[R](handler: AmqpEvent => ZIO[R, Nothing, Unit]): ZIO[R, Nothing, Unit] =
          for {
            queue <- Queue.unbounded[AmqpEvent]
                _ <- (queue.take >>= handler).forever.ensuring(queue.shutdown <* handlers.update(_.filterNot(_ == queue))).interruptChildren.fork
                _ <- handlers.update(queue :: _)
          } yield ()
      }
  }
}
