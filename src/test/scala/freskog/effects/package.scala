package freskog

import freskog.effects.app.dto.ResultEvent
import freskog.effects.app.logger.{FakeLogger, LogLevel}
import freskog.effects.app.services.commands.FakeResultPublisher
import freskog.effects.domain.calculator.Calculator
import freskog.effects.domain.formatter.ResultFormatter
import zio.ZIO
import zio.blocking.Blocking
import zio.scheduler.Scheduler
import zio.test.mock.{MockClock, MockConsole, MockRandom, MockSystem}

package object effects {

  type SpecEnv = Blocking
    with MockClock
    with MockConsole
    with MockRandom
    with Scheduler
    with MockSystem
    with ResultFormatter
    with FakeLogger
    with Calculator
    with FakeResultPublisher

  def getLastLogEntry(level: LogLevel): ZIO[FakeLogger, Nothing, String] =
    ZIO.accessM[FakeLogger](_.lastMessage(level))

  val getLastPublishedResultEvent: ZIO[FakeResultPublisher, Nothing, Option[ResultEvent]] =
    ZIO.accessM[FakeResultPublisher](_.getLastPublishedResultEvent)
}
