package freskog.effects.infra

import freskog.effects.app.dto.{ CalculatorCommand, ResultEvent }
import scalaz.zio.ZIO

package object api {

  val infraApiService: ZIO[InfraApi, Nothing, InfraApi.Service] =
    ZIO.access[InfraApi](_.infraApi)

  def handleCalculatorCommand[R1, E](handleCmd: CalculatorCommand => ZIO[R1, E, Unit]): ZIO[InfraApi with R1, E, Unit] =
    infraApiService.flatMap(_.handleCalculatorCommand(handleCmd))

  def handleResultEvent[R1, E](handleEv: ResultEvent => ZIO[R1, E, Unit]): ZIO[InfraApi with R1, E, Unit] =
    infraApiService.flatMap(_.handleResultEvent(handleEv))

  def publishResultEvent(ev: ResultEvent): ZIO[InfraApi, Nothing, Unit] =
    ZIO.accessM[InfraApi](_.infraApi.publishResultEvent(ev))
}
