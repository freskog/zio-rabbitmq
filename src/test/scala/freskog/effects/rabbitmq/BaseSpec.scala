package freskog.effects.rabbitmq

import java.io.IOException
import java.util.concurrent.TimeUnit

import com.rabbitmq.client.ShutdownSignalException
import freskog.effects.rabbitmq.admin.AdminClient
import freskog.effects.rabbitmq.events._
import freskog.effects.rabbitmq.publisher.Publisher.Inflight
import org.scalatest.events.TestFailed
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{Assertion, DiagrammedAssertions, FlatSpec, Matchers}
import scalaz.zio._
import scalaz.zio.clock.Clock
import scalaz.zio.duration.Duration

import scala.concurrent.TimeoutException

class BaseSpec extends FlatSpec with DiagrammedAssertions with Matchers {

  type TestEnv = AdminClient with Events with Clock with Inflight

  def testEnv[E, A](queue: List[Option[String]]): UIO[TestEnv] =
    for {
      eventsEnv <- Events.makeEvents
      seqNoRef  <- Ref.make[Long](0)
      pending   <- enqueueAsEvents(queue)
      inflight  <- RefM.make[Map[Long, Promise[IOException, Unit]]](Map.empty)
    } yield new Clock with Events with FakeAdminClient with Inflight {
      override val messageQueue: Queue[Option[MessageReceived]] = pending
      override val seqNo: Ref[Long]                             = seqNoRef
      override val events: Events.Service[Any]                  = eventsEnv.events
      override val clock: Clock.Service[Any]                    = Clock.Live.clock
      override val toConfirm: RefM[Map[FiberId, Promise[IOException, Unit]]] = inflight
    }

  val realRts: DefaultRuntime =
    new DefaultRuntime {}

  def enqueueAsEvents(queued: List[Option[String]]): UIO[Queue[Option[MessageReceived]]] =
    for {
      pending        <- Queue.unbounded[Option[MessageReceived]]
      pendingCounter <- Ref.make[Long](0)
      messages <- ZIO.foreach(queued) {
                   case None      => UIO.succeed(None)
                   case Some(msg) => pendingCounter.modify(n => (Some(MessageReceived(n, false, msg)), n + 1))
                 }
      _ <- pending.offerAll(messages)
    } yield pending

  def run[E <: Throwable, A](queued: List[String])(z: ZIO[TestEnv, E, A]): A =
    realRts.unsafeRun(
      testEnv(queued.map(Option(_))) >>=
        z.interruptChildren.timeoutFail(new TimeoutException("Test didn't complete"))(Duration(3, TimeUnit.SECONDS)).provide
    )

  val done: ZIO[TestEnv, Nothing, Boolean]     = ZIO.succeed(true)
  val continue: ZIO[TestEnv, Nothing, Boolean] = ZIO.succeed(false)

  val sig: ShutdownSignalException =
    null.asInstanceOf[ShutdownSignalException]


  def failWith(p: Promise[TestFailedException, Unit])(e: AmqpEvent): ZIO[TestEnv, Nothing, Boolean] =
    failWithMsg(p)(fail(s"unexpected event $e")) *> ZIO.succeed(true)

  def failWithMsg(p: Promise[TestFailedException, Unit])(msg: String): UIO[Unit] =
    ZIO.effect(fail(msg)).refineOrDie[TestFailedException]{ case e:TestFailedException => e}.tapError(p.fail).option.unit

  def runAsForkUntilDone[E <: Throwable, A](
    queued: List[String]
  )(z: ZIO[TestEnv, E, A])(pf: PartialFunction[AmqpEvent, ZIO[TestEnv, Nothing, Boolean]]): Assertion =
    run(queued) {
      for {
        p <- Promise.make[TestFailedException, Unit]
        _ <- subscribe[TestEnv](pf.applyOrElse(_, failWith(p)) >>= (end => if (end) p.succeed(()).unit else UIO.unit))
        _ <- z.fork
        _ <- p.await
      } yield succeed
    }

  def runAsForkWithExpectedEvents[E <: Throwable, A](queued: List[String])(z: ZIO[TestEnv, E, A])(expected: List[AmqpEvent]): Assertion =
    run(queued) {
      for {
        p         <- Promise.make[TestFailedException, Unit]
        received  <- Ref.make[List[AmqpEvent]](Nil)
        remaining <- Ref.make[List[AmqpEvent]](expected)
        _ <- subscribe { ev =>
              ZIO.whenM(remaining.get.map(_.nonEmpty)) {
                for {
                  currReceived  <- received.get
                  currRemaining <- remaining.get
                  errMsg = s"Unexpected event '$ev', expected '${currRemaining.head}' (prev received: $currReceived)"
                  _ <- ZIO.when(currRemaining.head != ev)(failWithMsg(p)(errMsg))
                  _ <- ZIO.when(currRemaining.head == ev)(received.update(_ ::: List(ev)) *> remaining.update(_.tail))
                  _ <- ZIO.whenM(remaining.get.map(_.isEmpty))(p.succeed(()))
                } yield ()
              }
            }
        _ <- z.fork
        _ <- p.await
      } yield succeed
    }

}
