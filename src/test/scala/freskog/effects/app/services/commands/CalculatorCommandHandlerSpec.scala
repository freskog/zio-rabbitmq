package freskog.effects.app.services.commands

import freskog.effects._
import freskog.effects.app.dto._
import zio.test._

object CalculatorCommandHandlerSpec
    extends BaseRunnableSpec({
      suite("CommandHandler")(
        testM("A 'CalculateCurrentValue' command results in an ComputedTotal(0) event") {
          val cmd = CalculatorCommandHandler.createCalculatorCommandHandler >>= processCommand(CalculateCurrentValue).provide
          assertM(cmd *> getLastPublishedResultEvent, Predicate.isSome(Predicate.equals[ResultEvent](ComputedTotal(0))))
        },
        testM("An 'IncrementByOne' command results in an IncrementedTo(1) event") {
          val cmd = CalculatorCommandHandler.createCalculatorCommandHandler >>= processCommand(IncrementByOne).provide
          assertM(cmd *> getLastPublishedResultEvent, Predicate.isSome(Predicate.equals[ResultEvent](IncrementedTo(1))))
        }
      )
    })
