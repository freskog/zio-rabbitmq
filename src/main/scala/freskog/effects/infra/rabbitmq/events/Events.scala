package freskog.effects.infra.rabbitmq.events

import scalaz.zio._

trait Events extends Serializable {
  val events:Events.Service
}

object Events {

  trait Service extends Serializable {
    def publish(e:AmqpEvent): IO[Nothing, Unit]
    def subscribe[R](handler: AmqpEvent => ZIO[R,Nothing,Unit]):ZIO[R, Nothing, Unit]
    def subscribeSome[R](handler: PartialFunction[AmqpEvent, ZIO[R, Nothing, Unit]]):ZIO[R,Nothing, Unit]
  }

  def makeEvents:UIO[Events] =
    Ref.make[List[Queue[AmqpEvent]]](Nil) >>= { ref =>
      ZIO.effectTotal(new Live { override val handlers: Ref[List[Queue[AmqpEvent]]] = ref })
    }

  trait Live extends Events {

    val handlers:Ref[List[Queue[AmqpEvent]]]

    override val events: Service =
      new Service {
        override def publish(e: AmqpEvent): ZIO[Any, Nothing, Unit] =
         handlers.get.flatMap(ZIO.foreach_(_)(_ offer e))

        override def subscribeSome[R](pf:PartialFunction[AmqpEvent, ZIO[R, Nothing, Unit]]):ZIO[R, Nothing, Unit] =
          subscribe(pf.applyOrElse(_, (_:AmqpEvent) => ZIO.unit))

        override def subscribe[R](handler: AmqpEvent => ZIO[R, Nothing, Unit]): ZIO[R, Nothing, Unit] =
          for {
            queue <- Queue.unbounded[AmqpEvent]
                _ <- (queue.take >>= handler).forever.ensuring(handlers.update(_.filterNot(_ == queue)) *> queue.shutdown).fork
                _ <- handlers.update(queue :: _)
          } yield ()
      }
  }
}
