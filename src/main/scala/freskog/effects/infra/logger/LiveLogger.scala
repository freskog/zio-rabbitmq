package freskog.effects.infra.logger

import freskog.effects.app.logger.Logger
import freskog.effects.app.logger.Logger.Service
import zio.{UIO, ZIO}
import zio.clock.Clock
import zio.console.Console

object LiveLogger {
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
