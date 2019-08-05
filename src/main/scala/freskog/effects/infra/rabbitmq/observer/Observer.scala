package freskog.effects.infra.rabbitmq.observer

import zio._

trait Observer extends Serializable {
  val observer: Observer.Service[Any]
}

object Observer {

  trait Service[R] extends Serializable {
    def notifyOf(e: AmqpEvent): ZIO[R, Nothing, Unit]
    def handle[R1, E](handler: AmqpEvent => ZIO[R1, E, Unit]): ZIO[R with R1, E, Nothing]
    def handleSome[R1, E](handler: PartialFunction[AmqpEvent, ZIO[R1, E, Unit]]):ZIO[R with R1, E, Nothing]
  }

  def makeObserver: UIO[Observer] =
    Ref.make[List[Queue[AmqpEvent]]](Nil) >>= { ref =>
      ZIO.effectTotal(new Live { override val handlers: Ref[List[Queue[AmqpEvent]]] = ref })
    }

  trait Live extends Observer {

    val handlers: Ref[List[Queue[AmqpEvent]]]

    override val observer: Service[Any] =
      new Service[Any] {

        def addQueue(): ZIO[Any, Nothing, Queue[AmqpEvent]] =
          for {
            queue <- Queue.unbounded[AmqpEvent]
            _     <- handlers.update(queue :: _)
          } yield queue

        def removeQueue(queue:Queue[AmqpEvent]): ZIO[Any, Nothing, Unit] =
          handlers.update(_.filterNot(_ == queue)) *> queue.shutdown

        override def notifyOf(e: AmqpEvent): ZIO[Any, Nothing, Unit] =
          handlers.get.flatMap(ZIO.foreach_(_)(_ offer e))

        override def handle[R1, E](handler: AmqpEvent => ZIO[R1, E, Unit]): ZIO[R1, E, Nothing] =
          addQueue >>= (queue => (queue.take >>= handler).forever.ensuring(removeQueue(queue)))

        override def handleSome[R1, E](handler: PartialFunction[AmqpEvent, ZIO[R1, E, Unit]]): ZIO[R1, E, Nothing] =
          handle[R1,E](handler.applyOrElse(_, (_:AmqpEvent) => ZIO.unit))
      }
  }
}
