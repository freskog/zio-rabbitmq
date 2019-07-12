package freskog.effects.app.services

import freskog.effects.app.dto.ResultEvent
import zio.ZIO

package object results {

  def processResult(ev: ResultEvent): ZIO[ResultHandler, Nothing, Unit] =
    ZIO.accessM[ResultHandler](_.resultEventHandler.processResult(ev))
}
