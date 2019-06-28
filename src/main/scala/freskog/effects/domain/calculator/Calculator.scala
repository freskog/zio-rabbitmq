package freskog.effects.domain.calculator

import scalaz.zio.{ Ref, ZIO }

trait Calculator extends Serializable {
  val calculator: Calculator.Service
}


object Calculator extends Serializable {

  trait Live extends Calculator {
    override val calculator: Service =
      new Service {
        override def incrementByOne(state: Ref[Int]): ZIO[Any, Nothing, Int] =
          state.update(_ + 1)

        override def computeCurrentValue(state: Ref[Int]): ZIO[Any, Nothing, Int] =
          state.get
      }
  }

  trait Service extends Serializable {
    def computeCurrentValue(state: Ref[Int]): ZIO[Any, Nothing, Int]
    def incrementByOne(state: Ref[Int]): ZIO[Any, Nothing, Int]
  }

}
