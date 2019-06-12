package freskog.effects.app

import freskog.effects.calculator.ResultEvent
import scalaz.zio.{ UIO, ZIO }

package object services {

  val resultPrinter: ZIO[ResultPrinter, Nothing, ResultPrinter.Service[Any]] =
    ZIO.access[ResultPrinter](_.printer)

  def print(msg: String): ZIO[ResultPrinter, Nothing, Unit] =
    ZIO.accessM[ResultPrinter](_.printer.print(msg))

  val calculatorInput: ZIO[CalculatorInput, Nothing, CalculatorInput.Service[Any]] =
    ZIO.access[CalculatorInput](_.calculatorInput)

  def handleCalculatorInput(startValue: Int): ZIO[CalculatorInput, Nothing, String => UIO[List[ResultEvent]]] =
    ZIO.accessM[CalculatorInput](_.calculatorInput.handleCalculatorInput(startValue))

}
