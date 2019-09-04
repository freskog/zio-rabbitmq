package freskog.effects

import freskog.effects.app.dto.ResultEvent
import freskog.effects.app.logger.{ FakeLogger, LogMessage }
import freskog.effects.app.services.commands.FakeResultPublisher
import freskog.effects.domain.calculator.Calculator
import freskog.effects.domain.formatter.ResultFormatter
import zio.blocking.Blocking
import zio.test.mock._
import zio.test.{ TestExecutor, TestRunner, ZTest }
import zio.{ Managed, Ref }

object AppTestRunner
    extends TestRunner[String, ZTest[SpecEnv, Any, Any], Any, Any](
      TestExecutor.managed(AppBaseEnv.baseTestEnv)
    ) {}

object AppBaseEnv {
  val baseTestEnv: Managed[Nothing, SpecEnv] =
    Ref.make[List[ResultEvent]](Nil).toManaged_ >>= { events =>
      Ref.make[List[LogMessage]](Nil).toManaged_ >>= { logs =>
        MockEnvironment.Value.map(
          env =>
            new Blocking with MockClock with MockConsole with MockRandom with MockSystem with ResultFormatter with FakeLogger with Calculator
            with FakeResultPublisher {
              override val blocking: Blocking.Service[Any]               = env.blocking
              override val clock: MockClock.Service[Any]                 = env.clock
              override val scheduler: MockClock.Service[Any]             = env.scheduler
              override val console: MockConsole.Service[Any]             = env.console
              override val random: MockRandom.Service[Any]               = env.random
              override val system: MockSystem.Service[Any]               = env.system
              override val formatter: ResultFormatter.Service            = ResultFormatter.createResultFormatter.formatter
              override val calculator: Calculator.Service[Any]           = Calculator.createCalculator.calculator
              override val capturedLogs: Ref[List[LogMessage]]           = logs
              override val publishedResultEvents: Ref[List[ResultEvent]] = events
            }
        )
      }
    }
}
