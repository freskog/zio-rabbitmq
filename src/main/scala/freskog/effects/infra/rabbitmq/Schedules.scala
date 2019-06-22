package freskog.effects.infra.rabbitmq

import java.io.IOException
import java.util.concurrent.TimeUnit

import freskog.effects.infra.logger._
import freskog.effects.infra.rabbitmq.events.MessageReceived
import scalaz.zio.console.{Console, putStrLn}
import scalaz.zio.duration.Duration
import scalaz.zio.{Exit, Schedule, UIO, ZSchedule}

object Schedules {

  import Schedule._

  val every500ms: Schedule[Any, Int] =
    Schedule.spaced(Duration(500, TimeUnit.MILLISECONDS))

  val untilNonEmpty: Schedule[Option[MessageReceived], MessageReceived] =
    every500ms *> Schedule.doUntil[Option[MessageReceived]](_.nonEmpty).map(_.get)

  val exponentialStartingAt500ms: Schedule[Exit.Cause[IOException], Duration] = exponential(Duration(500, TimeUnit.MILLISECONDS))
  val everyMinute: Schedule[Exit.Cause[IOException], Int]                     = spaced(Duration(1, TimeUnit.MINUTES))

  def restartFiber(component: String): ZSchedule[Logger, Exit.Cause[IOException], Unit] =
    logBefore(component) *> (exponentialStartingAt500ms || everyMinute).logOutput(logAfter(component)).unit

  def logBefore(component: String): ZSchedule[Logger, Exit.Cause[IOException], Exit.Cause[IOException]] =
    ZSchedule.logInput[Logger, Exit.Cause[IOException]] {
      case c if c.died        => warn(s"$component unexpectedly died, full trace is ${c.prettyPrint}")
      case c if c.failed      => warn(s"$component encountered failure ${c.squash}")
      case c if c.interrupted => warn(s"$component was interrupted")
      case c                  => warn(s"$component terminated with unknown cause, full trace is ${c.prettyPrint}")
    }

  def logAfter(name: String): ((Duration, Int)) => UIO[Unit] = {
    case (d, n) => putStrLn(s"Offset ${d.asScala.toCoarsest}, attempting restart of $name (retry #$n)").provide(Console.Live)
  }

}
