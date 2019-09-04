package freskog.effects.app.services

import freskog.effects.app.dto.{CalculatorCommand, ResultEvent}
import zio.ZIO

package object commands extends CalculatorCommandHandler.Service[CalculatorCommandHandler] {

  def processCommand(command: CalculatorCommand): ZIO[CalculatorCommandHandler, Nothing, Unit] =
    ZIO.accessM[CalculatorCommandHandler](_.calculatorCommandHandler.processCommand(command))

  def publishResultEvent(event: ResultEvent): ZIO[ResultPublisher, Nothing, Unit] =
    ZIO.accessM[ResultPublisher](_.resultPublisher.publishResult(event))
}
