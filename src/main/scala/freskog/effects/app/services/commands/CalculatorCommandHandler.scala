package freskog.effects.app.services.commands

import freskog.effects.app.dto._
import freskog.effects.domain.calculator._
import zio.{ Ref, ZIO }

trait CalculatorCommandHandler extends Serializable {
  val calculatorCommandHandler: CalculatorCommandHandler.Service[Any]
}

object CalculatorCommandHandler extends Serializable {
  self =>

  trait Service[R] {
    def processCommand(command: CalculatorCommand): ZIO[R, Nothing, Unit]
  }

  def createCalculatorCommandHandler: ZIO[ResultPublisher with Calculator, Nothing, CalculatorCommandHandler] =
    for {
      state <- Ref.make[Int](0)
      env   <- ZIO.environment[ResultPublisher with Calculator]
    } yield new CalculatorCommandHandler {
      override val calculatorCommandHandler: Service[Any] = (command: CalculatorCommand) => self.processCommand(state)(command).provide(env)
    }

  def processCommand(state: Ref[Int]): CalculatorCommand => ZIO[Calculator with ResultPublisher, Nothing, Unit] = {
    case IncrementByOne        => incrementByOne(state).map(IncrementedTo) >>= publishResultEvent
    case CalculateCurrentValue => computeCurrentValue(state).map(ComputedTotal) >>= publishResultEvent
  }
}
