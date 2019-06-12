package freskog.effects.app.services

import freskog.effects.calculator._
import scalaz.zio.{ Ref, UIO, ZIO }

trait CalculatorInput extends Serializable {
  val calculatorInput: CalculatorInput.Service[Any]
}

object CalculatorInput extends Serializable {
  self =>

  trait Service[R] extends Serializable {
    def handleCalculatorInput(startValue: Int): ZIO[Any, Nothing, String => UIO[List[ResultEvent]]]
  }

  trait Live extends CalculatorInput with Calculator { env =>
    override val calculatorInput: Service[Any] =
      (startValue: Int) =>
        for { state <- Ref.make[Int](startValue) } yield (str: String) => ZIO.foreach(processCommand(state)(str))(_.provide(env))
  }

  def processCommand(state: Ref[Int]): String => List[ZIO[Calculator, Nothing, ResultEvent]] = {
    case "incrementByOne"      => List(incrementByOne(state))
    case "computeCurrentValue" => List(computeCurrentValue(state))
    case _                     => Nil
  }
}
