package freskog.effects.rabbitmq

import java.io.IOException
import java.util.concurrent.TimeUnit

import scalaz.zio.console.{ putStrLn, Console }
import scalaz.zio.duration.Duration
import scalaz.zio.{ Exit, Schedule, UIO, ZSchedule }

object Schedules {

  import Schedule._

  val exponentialStartingAt500ms: Schedule[Exit.Cause[IOException], Duration] = exponential(Duration(500, TimeUnit.MILLISECONDS))
  val everyMinute: Schedule[Exit.Cause[IOException], Int]                     = spaced(Duration(1, TimeUnit.MINUTES))

  def restartFiber(component: String): ZSchedule[Any, Exit.Cause[IOException], Unit] =
    logBefore(component) *> (exponentialStartingAt500ms || everyMinute).logOutput(logAfter(component)).unit

  def logBefore(component: String): Schedule[Exit.Cause[IOException], Exit.Cause[IOException]] =
    logInput[Any, Exit.Cause[IOException]] {
      case c if c.died        => printErrMsg(s"$component unexpectedly died, full trace is ${c.prettyPrint}")
      case c if c.failed      => printErrMsg(s"$component encountered failure ${c.squash}")
      case c if c.interrupted => printErrMsg(s"$component was interrupted")
      case c                  => printErrMsg(s"$component terminated with unknown cause, full trace is ${c.prettyPrint}")
    }

  def logAfter(name: String): ((Duration, Int)) => UIO[Unit] = {
    case (d, n) => putStrLn(s"Offset ${d.asScala.toCoarsest}, attempting restart of $name (retry #$n)").provide(Console.Live)
  }

  def printErrMsg(str: String): UIO[Unit] =
    putStrLn(str).provide(Console.Live)
}
