package freskog.effects.domain.calculator

import zio.{ Ref, ZIO }

trait Calculator extends Serializable {
  val calculator: Calculator.Service[Any]
}


object Calculator extends Serializable {

  trait Service[R] extends Serializable {
    def computeCurrentValue(state: Ref[Int]): ZIO[R, Nothing, Int]
    def incrementByOne(state: Ref[Int]): ZIO[R, Nothing, Int]
  }

  val createCalculator: Calculator = new Calculator {
    override val calculator: Service[Any] =
      new Service[Any] {
        override def incrementByOne(state: Ref[Int]): ZIO[Any, Nothing, Int] =
          state.update(_ + 1)

        override def computeCurrentValue(state: Ref[Int]): ZIO[Any, Nothing, Int] =
          state.get
      }
  }
}
