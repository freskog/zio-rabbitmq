package freskog.effects.app.services.commands

import freskog.effects.app.dto._
import freskog.effects.domain.calculator._
import freskog.effects.infra.api._
import scalaz.zio.{ Ref, ZIO }

trait CalculatorCommandHandler extends Serializable {
  val calculatorCommandHandler: CalculatorCommandHandler.Service[Any]
}

object CalculatorCommandHandler extends Serializable {
  self =>

  trait Service[R] extends Serializable {
    def processCommand(command: CalculatorCommand): ZIO[R, Nothing, Unit]
  }

  def makeLiveCalculatorCommandHandler(api: InfraApi): ZIO[Any, Nothing, CalculatorCommandHandler] =
    Ref.make[Int](0).map { state =>
      new CalculatorCommandHandler with Calculator.Live with InfraApi { env =>
        override val calculatorCommandHandler: Service[Any] = (command: CalculatorCommand) => self.processCommand(state)(command).provide(env)
        override val infraApi: InfraApi.Service[Any]        = api.infraApi
      }
    }

  def processCommand(state: Ref[Int]): CalculatorCommand => ZIO[Calculator with InfraApi, Nothing, Unit] = {
    case IncrementByOne        => incrementByOne(state).map(IncrementedTo) >>= publishResultEvent
    case CalculateCurrentValue => computeCurrentValue(state).map(ComputedTotal) >>= publishResultEvent
  }
}
