package freskog.effects.domain.formatter

trait ResultFormatter {
  val formatter: ResultFormatter.Service
}

object ResultFormatter {
  trait Service {
    def formatIncrementedTo(value: Int): String
    def formatComputedTotal(value: Int): String
  }

  trait Live extends ResultFormatter {
    override val formatter: Service =
      new Service {
        override def formatIncrementedTo(value: Int): String =
          s"After applying the increment by one command, the new total is '$value'"

        override def formatComputedTotal(value: Int): String =
          s"The current value has been computed to '$value'"
      }
  }
}
