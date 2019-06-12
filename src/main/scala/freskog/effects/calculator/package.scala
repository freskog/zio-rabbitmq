package freskog.effects

import scalaz.zio.{Ref, ZIO}

package object calculator {

  val calculatorService: ZIO[Calculator, Nothing, Calculator.Service[Any]] =
    ZIO.access[Calculator](_.calculator)

  def incrementByOne(state:Ref[Int]): ZIO[Calculator, Nothing, ResultEvent] =
    ZIO.accessM[Calculator](_.calculator.incrementByOne(state))

  def computeCurrentValue(state:Ref[Int]): ZIO[Calculator, Nothing, ResultEvent] =
    ZIO.accessM[Calculator](_.calculator.computeCurrentValue(state))
}
