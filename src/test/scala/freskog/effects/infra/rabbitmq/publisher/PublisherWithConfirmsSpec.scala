package freskog.effects.infra.rabbitmq.publisher

import java.io.IOException

import com.rabbitmq.client.{ ConfirmListener, ShutdownListener }
import freskog.effects.infra.rabbitmq.BaseSpec
import freskog.effects.infra.rabbitmq.events.{ ConfirmListenerAdded, _ }
import freskog.effects.infra.rabbitmq.publisher.LivePublisher._
import scalaz.zio._

class PublisherWithConfirmsSpec extends BaseSpec {

  behavior of "a publisher with confirms"

  it should "successfully publish and wait for ack by the broker" in {
    run(Nil) {
      for {
        messages    <- messageQueue
        listenerRef <- Ref.make[Option[ConfirmListener]](None)
        _           <- withConfirms("test-exchange", messages).fork
        _           <- subscribeSome(handleBrokerEvent(messages))
        _ <- subscribeSome {
              case ConfirmListenerAdded(listener)                        => listenerRef.set(Some(listener))
              case MessagePublished("test-exchange", "", "some-payload") => listenerRef.get.map(_.get.handleAck(0, false))
            }
        promise <- Promise.make[IOException, Unit]
        _       <- messages.offer(Message(promise, "some-payload"))
        _       <- promise.await
      } yield succeed
    }
  }

  it should "give an error when a message is published but then nacked by the broker" in {
    run(Nil) {
      for {
        messages    <- messageQueue
        listenerRef <- Ref.make[Option[ConfirmListener]](None)
        _           <- subscribeSome(handleBrokerEvent(messages))
        _ <- subscribeSome {
              case ConfirmListenerAdded(listener)                        => listenerRef.set(Some(listener))
              case MessagePublished("test-exchange", "", "some-payload") => listenerRef.get.map(_.get.handleNack(0, false))
            }
        _       <- withConfirms("test-exchange", messages).fork
        promise <- Promise.make[IOException, Unit]
        _       <- messages.offer(Message(promise, "some-payload"))
        result  <- promise.await.either
      } yield result.isLeft should be(true)
    }
  }

  it should "give an error message when publishing fails" in {
    run(Nil) {
      for {
        messages    <- messageQueue
        listenerRef <- Ref.make[Option[ShutdownListener]](None)
        _           <- subscribeSome(handleBrokerEvent(messages))
        _           <- subscribeSome { case ShutdownListenerAdded(listener) => listenerRef.set(Some(listener)) }
        _           <- withConfirms("test-exchange", messages).ensuring(listenerRef.get.map(_.get.shutdownCompleted(sig))).fork
        promise     <- Promise.make[IOException, Unit]
        _           <- messages.offer(Message(promise, "fake-an-error-on-publish"))
        result      <- promise.await.either
      } yield result.isLeft should be(true)
    }
  }

}
