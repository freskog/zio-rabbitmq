package freskog.effects.app.dto

sealed abstract class CalculatorCommand
case object IncrementByOne        extends CalculatorCommand
case object CalculateCurrentValue extends CalculatorCommand

object CalculatorCommand {
  def fromString(str: String): Either[String, CalculatorCommand] =
    str match {
      case "incrementByOne"        => Right(IncrementByOne)
      case "calculateCurrentValue" => Right(CalculateCurrentValue)
      case invalid: String         => Left(s"Unable to parse CalculatorCommand from string '$invalid'")
    }
}
