package freskog.effects.app.logger

import zio._

trait Logger extends Serializable {
  val logger: Logger.Service[Any]
}

object Logger extends Serializable {
  trait Service[R] extends Serializable {
    def debug(msg: String): ZIO[R, Nothing, Unit]
    def info(msg: String): ZIO[R, Nothing, Unit]
    def warn(msg: String): ZIO[R, Nothing, Unit]
    def error(msg: String): ZIO[R, Nothing, Unit]
    def throwable(t: Throwable): ZIO[R, Nothing, Unit]
  }
}
