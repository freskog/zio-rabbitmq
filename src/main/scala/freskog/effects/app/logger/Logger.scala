package freskog.effects.app.logger

import zio._

trait Logger extends Serializable {
  val logger: Logger.Service
}

object Logger extends Serializable {
  trait Service extends Serializable {
    def debug(msg: String): UIO[Unit]
    def info(msg: String): UIO[Unit]
    def warn(msg: String): UIO[Unit]
    def error(msg: String): UIO[Unit]
    def throwable(t: Throwable): UIO[Unit]
  }
}
