package freskog.effects.app.services.commands

import freskog.effects.app.dto.ResultEvent
import zio.{Ref, ZIO}

trait FakeResultPublisher extends ResultPublisher {

  val publishedResultEvents: Ref[List[ResultEvent]]

  lazy val getLastPublishedResultEvent: ZIO[Any, Nothing, Option[ResultEvent]] =
    publishedResultEvents.get.map(_.headOption)

  lazy val allPublishedResultEvents: ZIO[Any, Nothing, List[ResultEvent]] =
    publishedResultEvents.get

  override val resultPublisher: ResultPublisher.Service[Any] =
    (ev: ResultEvent) => publishedResultEvents.update(ev :: _).unit
}
