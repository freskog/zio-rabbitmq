package freskog.effects.domain.formatter

trait ResultFormatter {
  val formatter: ResultFormatter.Service[Any]
}

object ResultFormatter {
  trait Service[R] {
    def formatIncrementedTo(value: Int): String
    def formatComputedTotal(value: Int): String
  }

  trait Live extends ResultFormatter {
    override val formatter: Service[Any] =
      new Service[Any] {
        override def formatIncrementedTo(value: Int): String =
          s"After applying the increment by one command, the new total is '$value'"

        override def formatComputedTotal(value: Int): String =
          s"The current value has been computed to '$value'"
      }
  }
}
