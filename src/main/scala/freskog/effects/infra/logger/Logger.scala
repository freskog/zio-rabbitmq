package freskog.effects.infra.logger

import zio._
import zio.clock._
import zio.console._

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

  def makeLogger(name: String): UIO[Logger] =
    UIO.effectTotal(org.slf4j.LoggerFactory.getLogger(name)).map { log =>
      new Logger with Console.Live with Clock.Live {

        override val logger: Service =
          new Service {
            override def debug(msg: String): UIO[Unit] =
              fmtString(msg) >>= (str => UIO.effectTotal(log.debug(str)))

            override def info(msg: String): UIO[Unit] =
              fmtString(msg) >>= (str => UIO.effectTotal(log.info(str)))

            override def warn(msg: String): UIO[Unit] =
              fmtString(msg) >>= (str => UIO.effectTotal(log.warn(str)))

            override def error(msg: String): UIO[Unit] =
              fmtString(msg) >>= (str => UIO.effectTotal(log.error(str)))

            override def throwable(t: Throwable): UIO[Unit] =
              fmtString("Unhandled throwable") >>= (msg => ZIO.effectTotal(log.error(msg, t)))
          }
      }
    }

  def fmtString(msg: String): ZIO[Any, Nothing, String] =
    ZIO.descriptorWith(desc => UIO.succeed(s"Fiber #[${desc.id}] - $msg"))
}
