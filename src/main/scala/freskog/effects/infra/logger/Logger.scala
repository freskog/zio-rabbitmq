package freskog.effects.infra.logger

import scalaz.zio._
import scalaz.zio.clock._
import scalaz.zio.console._

trait Logger extends Serializable {
  val logger: Logger.Service[Any]
}

object Logger extends Serializable {
  trait Service[R] extends Serializable {
    def debug(msg: String): UIO[Unit]
    def info(msg: String): UIO[Unit]
    def warn(msg: String): UIO[Unit]
    def error(msg: String): UIO[Unit]
    def throwable(t: Throwable): UIO[Unit]
  }

  def makeLogger(name: String): UIO[Logger] =
    UIO.effectTotal(org.slf4j.LoggerFactory.getLogger(name)).map { log =>
      new Logger with Console.Live with Clock.Live {

        override val logger: Service[Any] =
          new Service[Any] {
            override def debug(msg: String): UIO[Unit] =
              fmtString(msg).map(log.debug)

            override def info(msg: String): UIO[Unit] =
              fmtString(msg).map(log.info)

            override def warn(msg: String): UIO[Unit] =
              fmtString(msg).map(log.warn)

            override def error(msg: String): UIO[Unit] =
              fmtString(msg).map(log.error)

            override def throwable(t: Throwable): UIO[Unit] =
              fmtString("Unhandled throwable") >>= (msg => ZIO.effectTotal(log.error(msg, t)))
          }
      }
    }

  def fmtString(msg: String): ZIO[Any, Nothing, String] =
    ZIO.descriptorWith(desc => UIO.succeed(s"Fiber #[${desc.id}] - $msg"))
}
