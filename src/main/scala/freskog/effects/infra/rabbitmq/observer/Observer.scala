package freskog.effects.infra.rabbitmq.observer

import zio._

trait Observer extends Serializable {
  val observer:Observer.Service
}

object Observer {

  trait Service extends Serializable {
    def notifyOf(e:AmqpEvent): IO[Nothing, Unit]
    def listenTo[R](handler: AmqpEvent => ZIO[R,Nothing,Unit]):ZIO[R, Nothing, Unit]
    def listenToSome[R](handler: PartialFunction[AmqpEvent, ZIO[R, Nothing, Unit]]):ZIO[R,Nothing, Unit]
  }

  def makeObserver:UIO[Observer] =
    Ref.make[List[Queue[AmqpEvent]]](Nil) >>= { ref =>
      ZIO.effectTotal(new Live { override val handlers: Ref[List[Queue[AmqpEvent]]] = ref })
    }

  trait Live extends Observer {

    val handlers:Ref[List[Queue[AmqpEvent]]]

    override val observer: Service =
      new Service {
        override def notifyOf(e: AmqpEvent): ZIO[Any, Nothing, Unit] =
         handlers.get.flatMap(ZIO.foreach_(_)(_ offer e))

        override def listenToSome[R](pf:PartialFunction[AmqpEvent, ZIO[R, Nothing, Unit]]):ZIO[R, Nothing, Unit] =
          listenTo(pf.applyOrElse(_, (_:AmqpEvent) => ZIO.unit))

        override def listenTo[R](handler: AmqpEvent => ZIO[R, Nothing, Unit]): ZIO[R, Nothing, Unit] =
          for {
            queue <- Queue.unbounded[AmqpEvent]
                _ <- (queue.take >>= handler).forever.ensuring(handlers.update(_.filterNot(_ == queue)) *> queue.shutdown).fork
                _ <- handlers.update(queue :: _)
          } yield ()
      }
  }
}
