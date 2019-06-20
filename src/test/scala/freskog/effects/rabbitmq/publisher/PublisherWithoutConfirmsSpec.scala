package freskog.effects.rabbitmq.publisher

import java.io.IOException

import freskog.effects.rabbitmq.BaseSpec
import freskog.effects.rabbitmq.publisher.Publisher._
import scalaz.zio.Promise

class PublisherWithoutConfirmsSpec extends BaseSpec {

  behavior of "a publisher without confirms"

  it should "publish a message successfully" in {
    run(Nil) {
      for {
        messages <- Publisher.messageQueue
        _        <- withoutConfirms("test-exchange", messages).fork
        p        <- Promise.make[IOException, Unit]
        _        <- messages.offer(Message(p, "TestPayload"))
        _        <- p.await
      } yield succeed
    }
  }

  it should "give an error on failing to publish" in {
    run(Nil) {
      for {
        messages <- Publisher.messageQueue
        _        <- withoutConfirms("test-exchange", messages).fork
        p        <- Promise.make[IOException, Unit]
        _        <- messages.offer(Message(p, "fake-an-error-on-publish"))
        result   <- p.await.either
      } yield result.isLeft should be (true)
    }
  }
}
