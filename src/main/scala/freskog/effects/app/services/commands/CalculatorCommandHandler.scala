package freskog.effects.app.services.commands

import freskog.effects.app.dto._
import freskog.effects.domain.calculator._
import zio.{Ref, ZIO}

trait CalculatorCommandHandler extends Serializable {
  val calculatorCommandHandler: CalculatorCommandHandler.Service
}

object CalculatorCommandHandler extends Serializable {
  self =>

  trait Service extends Serializable {
    def processCommand(command: CalculatorCommand): ZIO[Any, Nothing, Unit]
  }

  def makeLiveCalculatorCommandHandler(publisher:ResultPublisher): ZIO[Any, Nothing, CalculatorCommandHandler] =
    Ref.make[Int](0).map { state =>
      new CalculatorCommandHandler with Calculator.Live with ResultPublisher { env =>
        override val calculatorCommandHandler: Service = (command: CalculatorCommand) => self.processCommand(state)(command).provide(env)
        override val resultPublisher:ResultPublisher.Service = publisher.resultPublisher
      }
    }

  def processCommand(state: Ref[Int]): CalculatorCommand => ZIO[Calculator with ResultPublisher, Nothing, Unit] = {
    case IncrementByOne        => incrementByOne(state).map(IncrementedTo) >>= publishResultEvent
    case CalculateCurrentValue => computeCurrentValue(state).map(ComputedTotal) >>= publishResultEvent
  }
}
