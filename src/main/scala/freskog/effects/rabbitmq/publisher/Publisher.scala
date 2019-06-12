package freskog.effects.rabbitmq.publisher

import java.io.IOException

import com.rabbitmq.client._
import freskog.effects.rabbitmq.admin._
import freskog.effects.rabbitmq.topology._
import freskog.effects.rabbitmq.{ AmqpQueue => _, _ }
import scalaz.zio._
import scalaz.zio.clock.Clock

trait Publisher extends Serializable {
  val publisher: Publisher.Service[Any]
}

object Publisher extends Serializable {
  self =>

  trait Service[R] extends Serializable {
    def publishTo(exchange: String, topology: Declaration): ZIO[Any, Nothing, String => UIO[Unit]]
    def publishConfirmsTo(exchange: String, topology: Declaration): ZIO[Any, Nothing, String => ZIO[Any, IOException, Unit]]
  }

  val noKey: String                    = ""
  val emptyProps: AMQP.BasicProperties = new AMQP.BasicProperties.Builder().build()

  trait Live extends Publisher with TopologyClient with AdminClient with Clock { env =>

    override val publisher: Service[Any] =
      new Service[Any] {
        override def publishTo(exchange: String, topology: Declaration): ZIO[Any, Nothing, String => UIO[Unit]] =
          self.publishTo(exchange, topology).provide(env)

        override def publishConfirmsTo(
          exchange: String,
          topology: Declaration
        ): ZIO[Any, Nothing, String => ZIO[Any, IOException, Unit]] =
          self.publishConfirmsTo(exchange, topology).provide(env)
      }
  }

  type PublisherEnv = AdminClient with TopologyClient with Clock

  def publishTo(exchange: String, topology: Declaration): ZIO[PublisherEnv, Nothing, String => UIO[Unit]] =
    for {
      queue <- Queue.bounded[String](10)
      _     <- publishFiber(exchange, topology, queue)
    } yield (s: String) => queue.offer(s).unit

  def publishConfirmsTo(exchange: String, topology: Declaration): ZIO[PublisherEnv, Nothing, String => ZIO[Any, IOException, Unit]] =
    for {
      queue <- Queue.bounded[(Promise[IOException, Unit], String)](10)
      _     <- publishWithConfirmsFiber(exchange, topology, queue)
    } yield (s: String) =>
      for {
        promise <- Promise.make[IOException, Unit]
        _       <- queue.offer((promise, s))
        _       <- promise.await
      } yield ()

  def publishWithConfirmsFiber(
    exchange: String,
    topology: Declaration,
    queue: Queue[(Promise[IOException, Unit], String)]
  ): ZIO[PublisherEnv, Nothing, Fiber[IOException, Unit]] =
    createManagedChannel(exchange).use { channel =>
      for {
        rts      <- ZIO.runtime[Any]
        inflight <- RefM.make[Map[Long, Promise[IOException, Unit]]](Map.empty)
        _        <- createTopology(topology)
        _        <- addConfirmListener(channel, makeConfirmListener(channel, rts, inflight))
        _        <- addShutdownListener(channel, makeShutdownListener(queue, rts, inflight))
        _        <- confirmSelect(channel)
        _        <- publishMsg(channel, exchange, queue, inflight).forever
      } yield ()
    }.sandbox.retry(Schedules.restartFiber(exchange)).option.unit.fork

  def publishMsg(
    channel: Channel,
    ex: String,
    queue: Queue[(Promise[IOException, Unit], String)],
    inflight: RefM[Map[Long, Promise[IOException, Unit]]]
  ): ZIO[AdminClient, IOException, Promise[IOException, Unit]] =
    for {
      promiseAndMsg  <- queue.take
      (promise, msg) = promiseAndMsg
      seqNo          <- getNextPublishSeqNo(channel)
      _              <- inflight.update(m => ZIO.succeed(m.updated(seqNo, promise)))
      _              <- basicPublish(channel, ex, msg.getBytes()) tapError promise.fail
    } yield promise

  def publishFiber(
    exchange: String,
    topology: Declaration,
    queue: Queue[String]
  ): ZIO[PublisherEnv, Nothing, Fiber[IOException, Unit]] =
    createManagedChannel(exchange).use { channel =>
      for {
        _ <- createTopology(topology)
        _ <- (queue.take.map(_.getBytes()) >>= (basicPublish(channel, exchange, _))).forever
      } yield ()
    }.sandbox.retry(Schedules.restartFiber(exchange)).option.unit.fork

  def makeConfirmListener(
    channel: Channel,
    rts: Runtime[Any],
    inflight: RefM[Map[Long, Promise[IOException, Unit]]]
  ): ConfirmListener =
    new ConfirmListener {

      def nackException(tag: Long) =
        new IOException(s"Message $tag was nacked")

      def ack(tag: FiberId): ZIO[Any, Nothing, Unit] =
        inflight.update(m => m(tag).succeed(()) *> ZIO.succeed(m - tag)).unit

      def nack(tag: FiberId): ZIO[Any, Nothing, Unit] =
        inflight.update(m => m(tag).fail(nackException(tag)) *> ZIO.succeed(m - tag)).unit

      def allTags(tag: FiberId)(
        action: FiberId => ZIO[Any, IOException, Unit]
      ): ZIO[Any, IOException, Map[FiberId, Promise[IOException, Unit]]] =
        inflight.update { m =>
          val tags = m.keys.filter(_ <= tag)
          ZIO.foreach(tags)(action) *> ZIO.succeed(m -- tags)
        }

      override def handleAck(deliveryTag: FiberId, multiple: Boolean): Unit =
        rts.unsafeRun(if (multiple) allTags(deliveryTag)(ack) else ack(deliveryTag))

      override def handleNack(deliveryTag: FiberId, multiple: Boolean): Unit =
        rts.unsafeRun(if (multiple) allTags(deliveryTag)(nack) else nack(deliveryTag))
    }

  def makeShutdownListener(
    queue: Queue[(Promise[IOException, Unit], String)],
    rts: Runtime[Any],
    inflight: RefM[Map[Long, Promise[IOException, Unit]]]
  ): ShutdownListener =
    (cause: ShutdownSignalException) =>
      rts.unsafeRun(
        for {
          m <- inflight.get
          _ <- ZIO.foreach(m.values)(_.fail(new IOException(cause)))
          p <- Promise.make[IOException, Unit]
          _ <- queue.offer((p, ""))
        } yield ()
      )

}
