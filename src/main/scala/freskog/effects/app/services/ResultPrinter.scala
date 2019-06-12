package freskog.effects.app.services

import scalaz.zio.UIO
import scalaz.zio.console.Console

trait ResultPrinter extends Serializable {
  val printer: ResultPrinter.Service[Any]
}

object ResultPrinter extends Serializable {

  trait Service[R] extends Serializable {
    def print(msg: String): UIO[Unit]
  }

  trait Live extends ResultPrinter with Console.Live { env =>
    override val printer: ResultPrinter.Service[Any] = console.putStrLn(_).provide(env)
  }

  object Live extends Live

}
