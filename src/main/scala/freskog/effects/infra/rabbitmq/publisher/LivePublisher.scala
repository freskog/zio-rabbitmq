package freskog.effects.infra.rabbitmq.publisher

import java.io.IOException

import com.rabbitmq.client._
import freskog.effects.app.logger.Logger
import freskog.effects.infra.rabbitmq.admin._
import freskog.effects.infra.rabbitmq.observer._
import freskog.effects.infra.rabbitmq.topology._
import freskog.effects.infra.rabbitmq.{ ClientFactory, Schedules }
import zio._
import zio.clock.Clock

object LivePublisher {

  type PublisherEnv = Observer with ClientProvider with Logger with Clock

  def publishMessage(topology: Declaration, exchange: String): ZIO[PublisherEnv, Nothing, String => IO[IOException, Unit]] =
    publishFiber(topology, exchange) *> ZIO.environment[Observer] map (env => publishFn(exchange)(_).provide(env))

  def publishFiber(topology: Declaration, exchange: String): ZIO[PublisherEnv, Nothing, Fiber[Nothing, Nothing]] =
    ClientFactory
      .buildClientsFor(exchange)
      .use(initializePublisherOn(topology, exchange).provide)
      .sandbox
      .retry(Schedules.loggingRetries(exchange))
      .option
      .forever
      .fork

  def publishFn(exchange: String): String => ZIO[Observer, IOException, Unit] =
    (msg: String) =>
      for {
        confirmed <- Promise.make[IOException, Unit]
        _         <- notifyOf(MessageQueued(exchange, "", msg, confirmed))
        _         <- confirmed.await
      } yield ()

  def initializePublisherOn(topology: Declaration, exchange: String): ZIO[TopologyClient with AdminClient with Observer, IOException, Nothing] =
    createTopology(topology) *> (confirmSelect tap notifyOf) *> addListeners(exchange) *>
      RefM.make[Map[Long, Promise[IOException, Unit]]](Map.empty) >>= { inflight =>
      handleSome {
        case MessageQueued(ex, key, msg, confirmed) => basicPublish(ex, key, msg.getBytes("UTF-8"), confirmed) >>= notifyOf
        case MessagePublished(tag, promise)         => addTag(tag, promise, inflight)
        case MessageAcked(tag, false)               => forTag(tag, inflight)(_.succeed(()))
        case MessageNacked(tag, false, _)           => forTag(tag, inflight)(_.fail(nackException(tag)))
        case MessageAcked(tag, true)                => forSmallerThan(tag, inflight)(_.succeed(()))
        case MessageNacked(tag, true, _)            => forSmallerThan(tag, inflight)(_.fail(nackException(tag)))
        case PublisherShutdownReceived(_, sig)      => forAllTags(inflight)(_.fail(new IOException(sig))) *> ZIO.fail(new IOException(sig))
      }
    }

  def addListeners(name: String): ZIO[AdminClient with Observer, IOException, Unit] =
    for {
      rts <- ZIO.runtime[Observer]
      listener <- ZIO.effectTotal {
                   new ConfirmListener with ShutdownListener {

                     override def handleAck(deliveryTag: Long, multiple: Boolean): Unit =
                       rts.unsafeRun(notifyOf(MessageAcked(deliveryTag, multiple)))

                     override def handleNack(deliveryTag: Long, multiple: Boolean): Unit =
                       rts.unsafeRun(notifyOf(MessageNacked(deliveryTag, multiple, requeue = false)))

                     override def shutdownCompleted(cause: ShutdownSignalException): Unit =
                       rts.unsafeRun(notifyOf(PublisherShutdownReceived(name, cause)))
                   }

                 }
      _ <- addConfirmListener(listener) tap notifyOf
      _ <- addShutdownListener(listener) tap notifyOf
    } yield ()

  def nackException(tag: Long): IOException =
    new IOException(s"Message $tag was nacked")

  def addTag(tag: Long, promise: Promise[IOException, Unit], inflight: RefM[Map[Long, Promise[IOException, Unit]]]): UIO[Unit] =
    inflight.update[Any, Nothing](m => ZIO.effectTotal(m.updated(tag, promise))).unit

  def forSmallerThan(tag: Long, inflight: RefM[Map[Long, Promise[IOException, Unit]]])(f: Promise[IOException, Unit] => UIO[Boolean]): UIO[Unit] =
    inflight.update { m =>
      val tags = m.keys.filter(_ <= tag)
      ZIO.foreach_(tags)(tag => m.get(tag).fold(ZIO.unit)(f andThen (_.unit))) *> ZIO.effectTotal(m -- tags)
    }.unit

  def forAllTags(inflight: RefM[Map[Long, Promise[IOException, Unit]]])(f: Promise[IOException, Unit] => UIO[Boolean]): UIO[Unit] =
    inflight.update(m => ZIO.foreach_(m.keys)(tag => m.get(tag).fold(ZIO.unit)(f andThen (_.unit))) *> ZIO.effectTotal(Map.empty)).unit

  def forTag(tag: Long, inflight: RefM[Map[Long, Promise[IOException, Unit]]])(f: Promise[IOException, Unit] => UIO[Boolean]): UIO[Unit] =
    inflight.update(m => m.get(tag).fold(ZIO.unit)(f andThen (_.unit)) *> ZIO.effectTotal(m - tag)).unit

}
