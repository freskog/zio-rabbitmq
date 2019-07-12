package freskog.effects.infra

import zio.ZIO

package object logger {

  val loggerService: ZIO[Logger, Nothing, Logger.Service] =
    ZIO.access[Logger](_.logger)

  def debug(msg:String):ZIO[Logger, Nothing, Unit] =
    ZIO.accessM[Logger](_.logger.debug(msg))

  def info(msg:String):ZIO[Logger, Nothing, Unit] =
    ZIO.accessM[Logger](_.logger.info(msg))

  def warn(msg:String):ZIO[Logger, Nothing, Unit] =
    ZIO.accessM[Logger](_.logger.warn(msg))

  def error(msg:String):ZIO[Logger, Nothing, Unit] =
    ZIO.accessM[Logger](_.logger.error(msg))

  def throwable(t:Throwable):ZIO[Logger, Nothing, Unit] =
    ZIO.accessM[Logger](_.logger.throwable(t))
}
