package freskog.effects.domain

import scalaz.zio.{ Ref, ZIO }

package object calculator {

  val calculatorService: ZIO[Calculator, Nothing, Calculator.Service] =
    ZIO.access[Calculator](_.calculator)

  def incrementByOne(state: Ref[Int]): ZIO[Calculator, Nothing, Int] =
    ZIO.accessM[Calculator](_.calculator.incrementByOne(state))

  def computeCurrentValue(state: Ref[Int]): ZIO[Calculator, Nothing, Int] =
    ZIO.accessM[Calculator](_.calculator.computeCurrentValue(state))
}
