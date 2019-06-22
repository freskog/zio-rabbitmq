package freskog.effects.app.services

import freskog.effects.app.dto.CalculatorCommand
import scalaz.zio.ZIO

package object commands {

  def processCommand(command: CalculatorCommand): ZIO[CalculatorCommandHandler, Nothing, Unit] =
    ZIO.accessM[CalculatorCommandHandler](_.calculatorCommandHandler.processCommand(command))
}
