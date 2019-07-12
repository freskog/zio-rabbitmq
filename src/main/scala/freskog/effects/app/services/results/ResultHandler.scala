package freskog.effects.app.services.results

import freskog.effects.app.dto.{ ComputedTotal, IncrementedTo, ResultEvent }
import freskog.effects.domain.formatter._
import freskog.effects.infra.logger._
import zio.{ UIO, ZIO }

trait ResultHandler extends Serializable {
  val resultEventHandler: ResultHandler.Service
}

object ResultHandler extends Serializable {

  trait Service extends Serializable {
    def processResult(ev: ResultEvent): UIO[Unit]
  }

  val makeLiveResultHandler: UIO[ResultHandler] =
    Logger.makeLogger("ResultHandler").map { log =>
      new ResultHandler with Logger with ResultFormatter.Live { env =>
        override val resultEventHandler: ResultHandler.Service = processResult(_).provide(env)
        override val logger: Logger.Service                    = log.logger
      }
    }

  def processResult(ev: ResultEvent): ZIO[Logger with ResultFormatter, Nothing, Unit] = ev match {
    case IncrementedTo(state) => formatIncrementedTo(state) >>= info
    case ComputedTotal(state) => formatComputedTotal(state) >>= info
  }

}
