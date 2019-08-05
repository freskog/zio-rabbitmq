package freskog.effects.domain

import zio.{ Ref, ZIO }

package object calculator extends Calculator.Service[Calculator] {

  val calculatorService: ZIO[Calculator, Nothing, Calculator.Service[Any]] =
    ZIO.access[Calculator](_.calculator)

  def incrementByOne(state: Ref[Int]): ZIO[Calculator, Nothing, Int] =
    calculatorService >>= (_ incrementByOne state)

  def computeCurrentValue(state: Ref[Int]): ZIO[Calculator, Nothing, Int] =
    calculatorService >>= (_ computeCurrentValue state)
}
