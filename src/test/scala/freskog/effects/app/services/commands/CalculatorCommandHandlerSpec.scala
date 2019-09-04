package freskog.effects.app.services.commands

import freskog.effects._
import freskog.effects.app.dto._
import zio.ZIO
import zio.test.Assertion.{equalTo, isSome}
import zio.test._

object CalculatorCommandHandlerSpec
    extends BaseRunnableSpec({
      suite("CommandHandler")(
        testM("A 'CalculateCurrentValue' command results in an ComputedTotal(0) event")(
          for {
            handler <- CalculatorCommandHandler.createCalculatorCommandHandler
            _       <- processCommand(CalculateCurrentValue).provide(handler)
            result  <- getLastPublishedResultEvent
          } yield assert[Option[ResultEvent]](result, isSome(equalTo(ComputedTotal(0))))
        ),
        testM("An 'IncrementByOne' command results in an IncrementedTo(1) event")(
          for {
            handler <- CalculatorCommandHandler.createCalculatorCommandHandler
            _       <- processCommand(IncrementByOne).provide(handler)
            result  <- getLastPublishedResultEvent
          } yield assert[Option[ResultEvent]](result, isSome(equalTo(IncrementedTo(1))))
        )
      )
    })
