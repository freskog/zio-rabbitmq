package freskog.effects.rabbitmq

import scalaz.zio.blocking.Blocking
import scalaz.zio.clock.Clock
import scalaz.zio.console.Console
import scalaz.zio.internal.{ Platform, PlatformLive }
import scalaz.zio.testkit.{ TestClock, TestConsole }
import scalaz.zio.{ Ref, Runtime }

case class TestRuntime(clockR: Ref[TestClock.Data], consoleR: Ref[TestConsole.Data]) extends Runtime[Clock with Console with Blocking] {

  type Environment = Clock with Console with Blocking

  val Platform: Platform = PlatformLive.Default
  val Environment: Environment =
    new Clock with Console with Blocking.Live {
      override val clock: Clock.Service[Any]     = TestClock(clockR)
      override val console: Console.Service[Any] = TestConsole(consoleR)
    }
}
