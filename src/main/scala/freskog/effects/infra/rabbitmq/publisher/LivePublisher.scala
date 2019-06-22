package freskog.effects.infra.rabbitmq.publisher

import java.io.IOException

import com.rabbitmq.client._
import freskog.effects.infra.logger.{ debug, error, warn, Logger }
import freskog.effects.infra.rabbitmq.Schedules
import freskog.effects.infra.rabbitmq.admin._
import freskog.effects.infra.rabbitmq.events._
import freskog.effects.infra.rabbitmq.topology.{ createTopology, Declaration, TopologyClient }
import scalaz.zio.clock.Clock
import scalaz.zio.{ Fiber, IO, Promise, Queue, RefM, Runtime, UIO, ZIO, ZManaged }

object LivePublisher {

  case class Message(confirmed: Promise[IOException, Unit], body: String)

  val maxInFlight                       = 10
  val noKey: String                     = ""
  val emptyProps: AMQP.BasicProperties  = new AMQP.BasicProperties.Builder().build()
  val messageQueue: UIO[Queue[Message]] = Queue.bounded[Message](maxInFlight)

  type PublisherEnv = AdminClient with TopologyClient with Events with Inflight with Clock with Logger

  def livePublisherEnv(cf: ConnectionFactory, name: String): ZManaged[Any, IOException, PublisherEnv] =
    for {
      adminEnv    <- AdminClient.makeLiveAdminClient(cf, name)
      eventsEnv   <- Events.makeEvents.toManaged_
      inflight    <- RefM.make[Map[Long, Promise[IOException, Unit]]](Map.empty).toManaged_
      topologyEnv = TopologyClient.makeLiveTopologyClientFrom(adminEnv, eventsEnv)
      loggerEnv   <- Logger.makeLogger("Publisher").toManaged_
    } yield new AdminClient with TopologyClient with Events with Inflight with Clock.Live with Logger {
      override val adminClient: AdminClient.Service[Any]                  = adminEnv.adminClient
      override val topologyClient: TopologyClient.Service[Any]            = topologyEnv.topologyClient
      override val events: Events.Service[Any]                            = eventsEnv.events
      override val toConfirm: RefM[Map[Long, Promise[IOException, Unit]]] = inflight
      override val logger: Logger.Service[Any]                            = loggerEnv.logger
    }

  val retryEnv: ZIO[Any, Nothing, Logger with Clock.Live] =
    Logger
      .makeLogger("Publisher")
      .map(
        env =>
          new Logger with Clock.Live {
            override val logger: Logger.Service[Any] = env.logger
          }
      )

  trait Inflight {
    val toConfirm: RefM[Map[Long, Promise[IOException, Unit]]]
  }

  val inflight: ZIO[Inflight, Nothing, RefM[Map[Long, Promise[IOException, Unit]]]] =
    ZIO.access[Inflight](_.toConfirm)

  def updateInflight[R, E](f: Map[Long, Promise[IOException, Unit]] => ZIO[R, E, Map[Long, Promise[IOException, Unit]]]) =
    inflight >>= (_.update(f))

  val getInflight: ZIO[Inflight, Nothing, Map[Long, Promise[IOException, Unit]]] =
    inflight >>= (_.get)

  trait Live extends Publisher { env =>

  }

  def publishTo(cf: ConnectionFactory, exchange: String, decl: Declaration): UIO[String => IO[IOException, Unit]] =
    for {
      messages  <- messageQueue
      logger    = subscribe(log(s"Publisher (without no-confirms) on '$exchange'"))
      events    = subscribeSome(handleBrokerEvent(messages))
      publisher = logger *> events *> createTopology(decl) *> withoutConfirms(exchange, messages)
      _         <- publisherFiber(cf, exchange, publisher)
    } yield createUserPublishFn(messages)

  def publishConfirmsTo(cf: ConnectionFactory, exchange: String, decl: Declaration): UIO[String => IO[IOException, Unit]] =
    for {
      messages  <- messageQueue
      prefix    = s"Publisher (with confirms) on '$exchange'"
      logger    = subscribe(log(prefix))
      events    = subscribeSome(handleBrokerEvent(messages))
      publisher = logger *> events *> createTopology(decl) *> withConfirms(exchange, messages)
      _         <- publisherFiber(cf, exchange, publisher)
    } yield createUserPublishFn(messages)

  def createUserPublishFn(messages: Queue[Message])(s: String): ZIO[Any, IOException, Unit] =
    for {
      p <- Promise.make[IOException, Unit]
      _ <- messages.offer(Message(p, s))
      _ <- p.await
    } yield ()

  def withoutConfirms(exchange: String, messages: Queue[Message]): ZIO[AdminClient, IOException, Nothing] =
    messages.take.flatMap { m =>
      basicPublish(exchange, "", m.body.getBytes("UTF-8"))
        .foldCauseM(cause => m.confirmed.fail(new IOException(cause.squash)), _ => m.confirmed.succeed(()))
    }.forever

  def withConfirms(exchange: String, messages: Queue[Message]): ZIO[Inflight with AdminClient with Events, IOException, Unit] =
    for {
      rts      <- ZIO.runtime[Events]
      listener <- makeListeners(rts, exchange)
      _        <- addConfirmListener(listener) tap publish
      _        <- addShutdownListener(listener) tap publish
      _        <- confirmSelect tap publish
      _        <- publishMsg(exchange, messages).forever
    } yield ()

  def publishMsg(ex: String, messages: Queue[Message]): ZIO[Inflight with AdminClient with Events, IOException, Unit] =
    for {
      msg   <- messages.take
      seqNo <- getNextPublishSeqNo tap publish
      _     <- updateInflight(m => ZIO.succeed(m.updated(seqNo.seqNo, msg.confirmed)))
      _     <- basicPublish(ex, "", msg.body.getBytes()) tap publish
    } yield ()

  def publisherFiber(cf: ConnectionFactory, name: String, publish: ZIO[PublisherEnv, IOException, Unit]): UIO[Fiber[Nothing, Unit]] =
    retryEnv >>= (env => livePublisherEnv(cf, name).use(publish.provide).sandbox.retry(Schedules.restartFiber(name)).provide(env).option.forever.fork)

  def log(prefix: String)(event: AmqpEvent): ZIO[Logger, Nothing, Unit] = event match {
    case MessageNacked(_, _, _)          => warn(s"$prefix - $event")
    case PublisherShutdownReceived(_, _) => error(s"$prefix - $event")
    case _: AmqpEvent                    => debug(s"$prefix - $event")
  }
  def handleBrokerEvent(messages: Queue[Message]): PartialFunction[AmqpEvent, ZIO[Inflight, Nothing, Unit]] = {
    case MessageAcked(deliveryTag, multiple)     => ack(deliveryTag, multiple)
    case MessageNacked(deliveryTag, multiple, _) => nack(deliveryTag, multiple)
    case PublisherShutdownReceived(_, reason)    => sendPoisonPill(reason, messages)
  }

  def makeListeners(rts: Runtime[Events], name: String): UIO[ConfirmListener with ShutdownListener] =
    ZIO.effectTotal {
      new ConfirmListener with ShutdownListener {

        override def handleAck(deliveryTag: Long, multiple: Boolean): Unit =
          rts.unsafeRun(publish(MessageAcked(deliveryTag, multiple)))

        override def handleNack(deliveryTag: Long, multiple: Boolean): Unit =
          rts.unsafeRun(publish(MessageNacked(deliveryTag, multiple, requeue = false)))

        override def shutdownCompleted(cause: ShutdownSignalException): Unit =
          rts.unsafeRun(publish(PublisherShutdownReceived(name, cause)))
      }
    }

  def sendPoisonPill(cause: ShutdownSignalException, messages: Queue[Message]): ZIO[Inflight, Nothing, Unit] =
    for {
      m <- getInflight
      _ <- ZIO.foreach(m.values)(_.fail(new IOException(cause)))
      p <- Promise.make[IOException, Unit] tap (_.succeed(()))
      _ <- messages.offer(Message(p, ""))
    } yield ()

  def nackException(tag: Long): IOException =
    new IOException(s"Message $tag was nacked")

  def ack(tag: Long, multiple: Boolean): ZIO[Inflight, Nothing, Unit] = {
    def ackTag(t: Long): ZIO[Inflight, Nothing, Unit] =
      updateInflight(m => m(t).succeed(()) *> UIO.succeed(m - t)).unit
    if (multiple) allTags(tag, ackTag).unit else ackTag(tag)
  }

  def nack(tag: Long, multiple: Boolean): ZIO[Inflight, Nothing, Unit] = {
    def nackTag(t: Long): ZIO[Inflight, Nothing, Unit] =
      updateInflight(m => m(tag).fail(nackException(tag)) *> ZIO.succeed(m - tag)).unit
    if (multiple) allTags(tag, nackTag).unit else nackTag(tag)
  }

  def allTags(tag: Long, action: Long => ZIO[Inflight, Nothing, Unit]): ZIO[Inflight, Nothing, Map[Long, Promise[IOException, Unit]]] =
    updateInflight { m =>
      val tags = m.keys.filter(_ <= tag)
      ZIO.foreach(tags)(action) *> ZIO.succeed(m -- tags)
    }

}
