package freskog.effects.app.services.results

import freskog.effects._
import freskog.effects.app.dto._
import freskog.effects.app.logger.Info
import freskog.effects.app.services.results.ResultHandler.createResultHandler
import zio.test._

object ResultHandlerSpec extends BaseRunnableSpec({
  suite("ResultHandler") (
    testM("An IncrementedTo(1) event is logged correctly") {
      val expected = "After applying the increment by one command, the new total is '1'"
      assertM((createResultHandler >>= (processResult(IncrementedTo(1)).provide)) *> getLastLogEntry(Info), Predicate.equals(expected))
    },
    testM("A ComputedTotal(0) event is logged correctly") {
      val expected = "The current value has been computed to '0'"
      assertM((createResultHandler >>= processResult(ComputedTotal(0)).provide) *> getLastLogEntry(Info), Predicate.equals(expected))
    }
  )
})
