package freskog.effects.calculator

import scalaz.zio.{Ref, ZIO}

trait Calculator extends Serializable {
  val calculator: Calculator.Service[Any]
}

sealed abstract class ResultEvent
final case class IncrementedTo(state: Int) extends ResultEvent
final case class ComputedTotal(state: Int) extends ResultEvent

object Calculator extends Serializable {

  trait Live extends Calculator {
    override val calculator: Service[Any] =
      new Service[Any] {
        override def incrementByOne(state: Ref[Int]): ZIO[Any, Nothing, ResultEvent] =
          state.modify( current => (IncrementedTo(current + 1), current + 1))

        override def computeCurrentValue(state: Ref[Int]): ZIO[Any, Nothing, ResultEvent] =
          state.get.map(ComputedTotal)
      }
  }

  trait Service[R] extends Serializable {
    def computeCurrentValue(state: Ref[Int]): ZIO[R, Nothing, ResultEvent]
    def incrementByOne(state: Ref[Int]): ZIO[R, Nothing, ResultEvent]
  }

}
