package freskog.effects.infra.rabbitmq

import java.io.IOException
import java.util.concurrent.TimeUnit

import freskog.effects.infra.logger._
import freskog.effects.infra.rabbitmq.events.MessageReceived
import zio.console.{ putStrLn, Console }
import zio.duration.Duration
import zio.{ Cause, Schedule, UIO, ZSchedule }

object Schedules {

  import Schedule._

  val every500ms: Schedule[Any, Int] =
    Schedule.spaced(Duration(500, TimeUnit.MILLISECONDS))

  val untilNonEmpty: Schedule[Option[MessageReceived], MessageReceived] =
    every500ms *> Schedule.doUntil[Option[MessageReceived]](_.nonEmpty).map(_.get)

  val exponentialStartingAt500ms: Schedule[Cause[IOException], Duration]     = exponential(Duration(500, TimeUnit.MILLISECONDS))
  val everyMinute: Schedule[Cause[IOException], Int]                         = spaced(Duration(1, TimeUnit.MINUTES))
  val cappedExponential: ZSchedule[Any, Cause[IOException], (Duration, Int)] = exponentialStartingAt500ms || everyMinute

  def restartFiber(component: String): ZSchedule[Logger, Cause[IOException], Unit] =
    logBefore(component) *> cappedExponential.logOutput[Logger](logAfter(component)).unit

  def logBefore(component: String): ZSchedule[Logger, Cause[IOException], Cause[IOException]] =
    ZSchedule.logInput[Logger, Cause[IOException]] {
      case c if c.died        => warn(s"$component unexpectedly died, full trace is ${c.prettyPrint}")
      case c if c.failed      => warn(s"$component encountered failure ${c.squash}")
      case c if c.interrupted => warn(s"$component was interrupted")
      case c                  => warn(s"$component terminated with unknown cause, full trace is ${c.prettyPrint}")
    }

  def logAfter(name: String): ((Duration, Int)) => UIO[Unit] = {
    case (d, n) => putStrLn(s"Offset ${d.asScala.toCoarsest}, attempting restart of $name (retry #$n)").provide(Console.Live)
  }

}
