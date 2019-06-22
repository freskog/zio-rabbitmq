package freskog.effects.domain

import scalaz.zio.ZIO

package object formatter {

  val resultFormatterService: ZIO[ResultFormatter, Nothing, ResultFormatter.Service[Any]] =
    ZIO.access[ResultFormatter](_.formatter)

  def formatIncrementedTo(value: Int): ZIO[ResultFormatter, Nothing, String] =
    ZIO.access[ResultFormatter](_.formatter.formatIncrementedTo(value))

  def formatComputedTotal(value: Int): ZIO[ResultFormatter, Nothing, String] =
    ZIO.access[ResultFormatter](_.formatter.formatComputedTotal(value))
}
