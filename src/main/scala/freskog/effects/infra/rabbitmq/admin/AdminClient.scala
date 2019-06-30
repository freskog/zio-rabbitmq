package freskog.effects.infra.rabbitmq.admin

import java.io.IOException
import java.util

import com.rabbitmq.client.{ Consumer => RConsumer, _ }
import freskog.effects.infra.logger._
import freskog.effects.infra.rabbitmq.events._
import scalaz.zio._
import scalaz.zio.blocking._
import scalaz.zio.clock._

import scala.concurrent.TimeoutException

trait AdminClient extends Serializable {
  val adminClient: AdminClient.Service
}

object AdminClient extends Serializable {
  self =>

  trait Service extends Serializable {
    def exchangeDeclare(name: String, `type`: BuiltinExchangeType): ZIO[Any, IOException, ExchangeDeclared]
    def queueDeclare(name: String): ZIO[Any, IOException, QueueDeclared]
    def queueBind(queue: String, exchange: String, routingKey: String): ZIO[Any, IOException, QueueBoundToExchange]
    def basicGet(queueName: String): ZIO[Any, IOException, Option[MessageReceived]]
    def basicAck(deliveryTag: Long, multiple: Boolean): ZIO[Any, IOException, MessageAcked]
    def basicConsume(queueName: String, consumer: RConsumer): ZIO[Any, IOException, ConsumerCreated]
    def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[Any, IOException, MessageNacked]
    def basicQos(prefetchCount: Int): ZIO[Any, IOException, QosEnabled]
    def basicPublish(exchange: String, routingKey: String, body: Array[Byte]): ZIO[Any, IOException, MessagePublished]
    def addConfirmListener(listener: ConfirmListener): ZIO[Any, Nothing, ConfirmListenerAdded]
    def addShutdownListener(listener: ShutdownListener): ZIO[Any, Nothing, ShutdownListenerAdded]
    def confirmSelect: ZIO[Any, IOException, ConfirmSelectEnabled.type]
    def getNextPublishSeqNo: ZIO[Any, Nothing, PublishSeqNoGenerated]
  }

  def makeLiveAdminClient(cf: ConnectionFactory, name: String): ZManaged[Any, IOException, AdminClient] =
    for {
      loggerEnv <- Logger.makeLogger("AdminClient").toManaged_
      conn      <- createManagedConnection(cf, name).provide(loggerEnv)
      chan      <- createManagedChannel(conn).provide(loggerEnv)
    } yield new Live {
      override val channel: Channel = chan
    }

  val convertToIOException: PartialFunction[Throwable, IOException] = {
    case io: IOException              => io
    case sig: ShutdownSignalException => new IOException(sig)
    case t: TimeoutException          => new IOException(t)
  }

  trait Live extends AdminClient with Blocking.Live with Clock.Live { env =>

    val channel: Channel

    override val adminClient: Service = new Service {
      import scala.collection.JavaConverters._

      val emptyProps: AMQP.BasicProperties = new AMQP.BasicProperties.Builder().build()
      val durable: Boolean                 = false
      val exclusive: Boolean               = false
      val autoDelete: Boolean              = false
      val noArgs: util.Map[String, AnyRef] = Map.empty[String, AnyRef].asJava

      def channelOp[A](f: Channel => A): ZIO[Any, IOException, A] =
        ZIO.effect(f(channel)).refineOrDie(convertToIOException)

      override def exchangeDeclare(name: String, `type`: BuiltinExchangeType): ZIO[Any, IOException, ExchangeDeclared] =
        channelOp(_.exchangeDeclare(name, `type`)) *> ZIO.succeed(ExchangeDeclared(name, `type`.getType))

      override def queueDeclare(name: String): ZIO[Any, IOException, QueueDeclared] =
        channelOp(_.queueDeclare(name, durable, exclusive, autoDelete, noArgs)) *> ZIO.succeed(QueueDeclared(name))

      override def queueBind(queue: String, exchange: String, routingKey: String): ZIO[Any, IOException, QueueBoundToExchange] =
        channelOp(_.queueBind(queue, exchange, routingKey)) *> ZIO.succeed(QueueBoundToExchange(queue, exchange, routingKey))

      override def basicGet(queueName: String): ZIO[Any, IOException, Option[MessageReceived]] =
        channelOp(_.basicGet(queueName, false)).map(
          Option(_).map(
            r => MessageReceived(r.getEnvelope.getDeliveryTag, r.getEnvelope.isRedeliver, new String(r.getBody, "UTF-8"))
          )
        )

      override def basicAck(deliveryTag: Long, multiple: Boolean): ZIO[Any, IOException, MessageAcked] =
        channelOp(_.basicAck(deliveryTag, multiple)) *> ZIO.succeed(MessageAcked(deliveryTag, multiple))

      override def basicConsume(queueName: String, consumer: RConsumer): ZIO[Any, IOException, ConsumerCreated] =
        channelOp(_.basicConsume(queueName, consumer)).map(ConsumerCreated(queueName, _, consumer))

      override def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[Any, IOException, MessageNacked] =
        channelOp(_.basicNack(deliveryTag, multiple, requeue)) *> ZIO.succeed(MessageNacked(deliveryTag, multiple, requeue))

      override def basicQos(prefetchCount: Int): ZIO[Any, IOException, QosEnabled] =
        channelOp(_.basicQos(prefetchCount)) *> ZIO.succeed(QosEnabled(prefetchCount))

      override def basicPublish(exchange: String, routingKey: String, body: Array[Byte]): ZIO[Any, IOException, MessagePublished] =
        channelOp(_.basicPublish(exchange, routingKey, emptyProps, body)) *> ZIO.succeed(
          MessagePublished(exchange, routingKey, new String(body, "UTF-8"))
        )

      override def addConfirmListener(listener: ConfirmListener): ZIO[Any, Nothing, ConfirmListenerAdded] =
        channelOp(_.addConfirmListener(listener)).orDie *> ZIO.succeed(ConfirmListenerAdded(listener))

      override def addShutdownListener(listener: ShutdownListener): ZIO[Any, Nothing, ShutdownListenerAdded] =
        channelOp(_.addShutdownListener(listener)).orDie *> ZIO.succeed(ShutdownListenerAdded(listener))

      override def confirmSelect: ZIO[Any, IOException, ConfirmSelectEnabled.type] =
        channelOp(_.confirmSelect()) *> ZIO.succeed(ConfirmSelectEnabled)

      override def getNextPublishSeqNo: UIO[PublishSeqNoGenerated] =
        channelOp(_.getNextPublishSeqNo).orDie.map(PublishSeqNoGenerated)
    }
  }

  def createManagedConnection(cf: ConnectionFactory, name: String): ZManaged[Logger, IOException, Connection] =
    ZManaged.make(newConnection(name, cf))(closeConnection)

  def createManagedChannel(conn: Connection): ZManaged[Logger, IOException, Channel] =
    ZManaged.make(createChannel(conn))(closeChannel)

  def closeChannel(chan: Channel): ZIO[Logger, Nothing, Unit] =
    ZIO.effect(chan.close()).catchAll(throwable)

  def createChannel(conn: Connection): ZIO[Logger, IOException, Channel] =
    ZIO.effect(conn.createChannel()).refineOrDie(convertToIOException)

  def closeConnection(conn: Connection): ZIO[Logger, Nothing, Unit] =
    ZIO.effect(conn.close()).catchAll(throwable)

  def newConnection(name: String, connectionFactory: ConnectionFactory): ZIO[Logger, IOException, Connection] =
    ZIO.effect(connectionFactory.newConnection(name)).refineOrDie(convertToIOException)

}
