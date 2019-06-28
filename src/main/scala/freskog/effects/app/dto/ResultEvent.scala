package freskog.effects.app.dto

import scala.util.matching.Regex

sealed abstract class ResultEvent
final case class IncrementedTo(state: Int) extends ResultEvent
final case class ComputedTotal(state: Int) extends ResultEvent

object ResultEvent {

  val incrementedTo: Regex = """IncrementedTo\((\d+)\)""".r
  val computedTotal: Regex = """ComputedTotal\((\d+)\)""".r

  def fromString(str: String): Either[String, ResultEvent] =
    str match {
      case incrementedTo(n) => Right(IncrementedTo(n.toInt))
      case computedTotal(n) => Right(ComputedTotal(n.toInt))
      case invalid: String  => Left(s"Unable to parse ResultEvent from invalid string '$invalid'")
    }
}
