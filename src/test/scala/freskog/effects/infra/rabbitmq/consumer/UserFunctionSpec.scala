package freskog.effects.infra.rabbitmq.consumer

import java.io.IOException

import freskog.effects.infra.rabbitmq.BaseSpec
import freskog.effects.infra.rabbitmq.consumer.LiveConsumer._
import freskog.effects.infra.rabbitmq.events._
import scalaz.zio.{ Promise, ZIO }

class UserFunctionSpec extends BaseSpec {

  behavior of "the createUserFunction"

  it should "ack a message after the user function succeeds" in {
    run(Nil) {
      for {
        m <- messageQueue
        p <- Promise.make[IOException, Unit]
        _ <- subscribeSome { case MessageAcked(0, false) => p.succeed(()).unit }
        _ <- subscribeSome(handleEventFromBroker(m))
        _ <- consumeWithUserFunction(ZIO.succeed(_), m).fork
        _ <- publish(MessageReceived(0, redelivered = false, "I-didn't-really-receive-this"))
        _ <- p.await
      } yield succeed
    }
  }

  it should "nack with requeue a message after the user function fails on a message which has not been redelivered" in {
    run(Nil) {
      for {
        m <- messageQueue
        p <- Promise.make[IOException, Unit]
        _ <- subscribe(handleEventFromBroker(m))
        _ <- subscribeSome { case MessageNacked(0, false, true) => p.succeed(()).unit }
        _ <- consumeWithUserFunction(_ => ZIO.fail("Oh noes, a user error!"), m).fork
        _ <- publish(MessageReceived(0, redelivered = false, "I-didn't-really-receive-this"))
        _ <- p.await
      } yield succeed
    }
  }

  it should "nack without requeue a message after the user function fails on a message which has been redelivered" in {
    run(Nil) {
      for {
        m <- messageQueue
        p <- Promise.make[IOException, Unit]
        _ <- subscribeSome(handleEventFromBroker(m))
        _ <- subscribeSome { case MessageNacked(0, false, false) => p.succeed(()).unit }
        _ <- consumeWithUserFunction(_ => ZIO.fail("Oh noes, a user error!"), m).fork
        _ <- publish(MessageReceived(0, redelivered = true, "I-didn't-really-receive-this"))
        _ <- p.await
      } yield succeed
    }
  }
}
