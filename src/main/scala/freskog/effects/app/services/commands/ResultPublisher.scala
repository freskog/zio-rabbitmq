package freskog.effects.app.services.commands

import freskog.effects.app.dto.ResultEvent
import zio.UIO

trait ResultPublisher {
  val resultPublisher:ResultPublisher.Service
}

object ResultPublisher {
  trait Service {
    def publishResult(ev:ResultEvent):UIO[Unit]
  }

  def fromPublishFn(f:ResultEvent => UIO[Unit]): ResultPublisher =
    new ResultPublisher {
      override val resultPublisher: Service = (ev: ResultEvent) => f(ev)
    }
}
