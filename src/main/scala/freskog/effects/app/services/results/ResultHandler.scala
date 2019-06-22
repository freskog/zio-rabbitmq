package freskog.effects.app.services.results

import freskog.effects.app.dto.{ComputedTotal, IncrementedTo, ResultEvent}
import freskog.effects.domain.formatter._
import scalaz.zio.{UIO, ZIO}
import scalaz.zio.console._

trait ResultHandler extends Serializable {
  val resultEventHandler: ResultHandler.Service[Any]
}

object ResultHandler extends Serializable {

  trait Service[R] extends Serializable {
    def processResult(ev: ResultEvent): UIO[Unit]
  }

  trait Live extends ResultHandler with Console.Live with ResultFormatter.Live { env =>
    override val resultEventHandler: ResultHandler.Service[Any] = processResult(_).provide(env)
  }

  def processResult(ev:ResultEvent): ZIO[Console with ResultFormatter, Nothing, Unit] = ev match {
    case IncrementedTo(state) => formatIncrementedTo(state) >>= putStrLn
    case ComputedTotal(state) => formatComputedTotal(state) >>= putStrLn
  }

  object Live extends Live

}
