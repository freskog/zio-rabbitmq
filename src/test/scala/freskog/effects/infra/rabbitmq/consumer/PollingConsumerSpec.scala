package freskog.effects.infra.rabbitmq.consumer

import freskog.effects.infra.rabbitmq.BaseSpec
import freskog.effects.infra.rabbitmq.observer.MessageReceived

class PollingConsumerSpec extends BaseSpec {

  behavior of "a polling consumer"

  it should "consume one pending message from the queue" in {
    runAsForkUntilDone(List("#1"))(LiveConsumer.pollForNewMessages("test-queue")) {
      case MessageReceived(0, false, "#1") => done
    }
  }

  it should "consume multiple pending messages from the queue" in {
    runAsForkWithExpectedEvents(List("#1", "#2"))(LiveConsumer.pollForNewMessages("test-queue")) {
      List(
        MessageReceived(seqNo = 0, redelivered = false, payload = "#1"),
        MessageReceived(seqNo = 1, redelivered = false, payload = "#2")
      )
    }
  }

}
