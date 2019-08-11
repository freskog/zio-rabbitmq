package freskog.effects.app.services.commands

import freskog.effects.app.dto.ResultEvent
import zio.{UIO, ZIO}

trait ResultPublisher {
  val resultPublisher:ResultPublisher.Service[Any]
}

object ResultPublisher {
  trait Service[R] {
    def publishResult(ev:ResultEvent):ZIO[R, Nothing, Unit]
  }

  def fromPublishFn(f:ResultEvent => UIO[Unit]): ResultPublisher =
    new ResultPublisher {
      override val resultPublisher: Service[Any] = (ev: ResultEvent) => f(ev)
    }
}
