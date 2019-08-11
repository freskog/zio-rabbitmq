package freskog.effects.app.logger
import zio.{ Ref, UIO, ZIO }

sealed abstract class LogLevel
case object Debug extends LogLevel
case object Info  extends LogLevel
case object Warn  extends LogLevel
case object Err   extends LogLevel

sealed abstract class LogPayload
case class Str(msg: String)     extends LogPayload
case class Thrown(t: Throwable) extends LogPayload

case class LogMessage(payload: LogPayload, level: LogLevel)

trait FakeLogger extends Logger {

  val capturedLogs: Ref[List[LogMessage]]

  def addLogMessage(payload: LogPayload, level: LogLevel): UIO[Unit] =
    capturedLogs.update(LogMessage(payload, level) :: _).unit

  def lastMessage(level: LogLevel): ZIO[Any, Nothing, String] =
    capturedLogs.get
      .map(_.collectFirst { case LogMessage(Str(msg), `level`) => msg })
      .someOrFail(s"No log message with $level level captured")
      .fold(identity, identity)

  override val logger: Logger.Service[Any] =
    new Logger.Service[Any] {
      override def debug(msg: String): ZIO[Any, Nothing, Unit] =
        addLogMessage(Str(msg), Debug)

      override def info(msg: String): ZIO[Any, Nothing, Unit] =
        addLogMessage(Str(msg), Info)

      override def warn(msg: String): ZIO[Any, Nothing, Unit] =
        addLogMessage(Str(msg), Warn)

      override def error(msg: String): ZIO[Any, Nothing, Unit] =
        addLogMessage(Str(msg), Err)

      override def throwable(t: Throwable): ZIO[Any, Nothing, Unit] =
        addLogMessage(Thrown(t), Err)
    }
}
