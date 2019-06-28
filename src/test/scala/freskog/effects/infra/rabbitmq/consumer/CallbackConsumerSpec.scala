package freskog.effects.infra.rabbitmq.consumer

import freskog.effects.infra.rabbitmq.BaseSpec
import freskog.effects.infra.rabbitmq.events._
import scalaz.zio.ZIO

class CallbackConsumerSpec extends BaseSpec {

  behavior of "a callback consumer"

  it should "configure QoS on init" in {
    runAsForkUntilDone(Nil)(LiveConsumer.initializeConsumerOn("test-queue")) {
      case QosEnabled(_) => done
    }
  }

  it should "publish messagereceived event when a message is received" in {
    runAsForkUntilDone(List("#1"))(LiveConsumer.initializeConsumerOn("test-queue")) {
      case QosEnabled(_)                   => continue
      case ConsumerCreated(_, _, _)        => continue
      case MessageReceived(0, false, "#1") => done
    }
  }

  it should "terminate correctly when shutdown by broker" in {
    runAsForkUntilDone(Nil)(LiveConsumer.initializeConsumerOn("test-queue")) {
      case QosEnabled(_)                      => continue
      case ConsumerCreated(name, _, consumer) => ZIO.effectTotal(consumer.handleShutdownSignal(name, sig)) *> continue
      case ConsumerShutdownReceived(_, _, _)  => done
    }
  }

  it should "terminate correctly when cancelled by user" in {
    runAsForkUntilDone(Nil)(LiveConsumer.initializeConsumerOn("test-queue")) {
      case QosEnabled(_)                      => continue
      case ConsumerCreated(name, _, consumer) => ZIO.effectTotal(consumer.handleCancelOk(name)) *> continue
      case SubscriberCancelledByUser(_, _)    => done
    }
  }

  it should "terminate correctly when cancelled by broker" in {
    runAsForkUntilDone(Nil)(LiveConsumer.initializeConsumerOn("test-queue")) {
      case QosEnabled(_)                      => continue
      case ConsumerCreated(name, _, consumer) => ZIO.effectTotal(consumer.handleCancel(name)) *> continue
      case SubscriberCancelledByBroker(_, _)  => done
    }
  }
}
