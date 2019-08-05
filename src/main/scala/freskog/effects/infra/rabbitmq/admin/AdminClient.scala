package freskog.effects.infra.rabbitmq.admin

import java.io.IOException
import java.util

import com.rabbitmq.client.{ Consumer => RConsumer, _ }
import freskog.effects.infra.rabbitmq.observer._
import zio._
import zio.blocking._

import scala.concurrent.TimeoutException

trait AdminClient extends Serializable {
  val adminClient: AdminClient.Service[Any]
}

object AdminClient extends Serializable {
  self =>

  trait Service[R] extends Serializable {
    def exchangeDeclare(name: String, `type`: BuiltinExchangeType): ZIO[R, IOException, ExchangeDeclared]
    def queueDeclare(name: String): ZIO[R, IOException, QueueDeclared]
    def queueBind(queue: String, exchange: String, routingKey: String): ZIO[R, IOException, QueueBoundToExchange]
    def basicAck(deliveryTag: Long, multiple: Boolean): ZIO[R, IOException, MessageAcked]
    def basicConsume(queueName: String, consumer: RConsumer): ZIO[R, IOException, ConsumerCreated]
    def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[R, IOException, MessageNacked]
    def basicQos(prefetchCount: Int): ZIO[R, IOException, QosEnabled]
    def basicPublish(
      exchange: String,
      routingKey: String,
      body: Array[Byte],
      confirmed: Promise[IOException, Unit]
    ): ZIO[R, IOException, MessagePublished]
    def addConfirmListener(listener: ConfirmListener): ZIO[R, Nothing, ConfirmListenerAdded]
    def addShutdownListener(listener: ShutdownListener): ZIO[R, Nothing, ShutdownListenerAdded]
    def confirmSelect: ZIO[R, IOException, ConfirmSelectEnabled.type]
  }

  val convertToIOException: PartialFunction[Throwable, IOException] = {
    case io: IOException              => io
    case sig: ShutdownSignalException => new IOException(sig)
    case t: TimeoutException          => new IOException(t)
  }

  trait Live extends AdminClient with Blocking { env =>

    val channel: Channel

    override val adminClient: Service[Any] = new Service[Any] {
      import scala.collection.JavaConverters._

      val emptyProps: AMQP.BasicProperties = new AMQP.BasicProperties.Builder().build()
      val durable: Boolean                 = false
      val exclusive: Boolean               = false
      val autoDelete: Boolean              = false
      val noArgs: util.Map[String, AnyRef] = Map.empty[String, AnyRef].asJava

      def channelOp[A](f: Channel => A): ZIO[Any, IOException, A] =
        effectBlocking(f(channel)).refineOrDie(convertToIOException).provide(env)

      override def exchangeDeclare(name: String, `type`: BuiltinExchangeType): ZIO[Any, IOException, ExchangeDeclared] =
        channelOp(_.exchangeDeclare(name, `type`)) *> ZIO.succeed(ExchangeDeclared(name, `type`.getType))

      override def queueDeclare(name: String): ZIO[Any, IOException, QueueDeclared] =
        channelOp(_.queueDeclare(name, durable, exclusive, autoDelete, noArgs)) *> ZIO.succeed(QueueDeclared(name))

      override def queueBind(queue: String, exchange: String, routingKey: String): ZIO[Any, IOException, QueueBoundToExchange] =
        channelOp(_.queueBind(queue, exchange, routingKey)) *> ZIO.succeed(QueueBoundToExchange(queue, exchange, routingKey))

      override def basicAck(deliveryTag: Long, multiple: Boolean): ZIO[Any, IOException, MessageAcked] =
        channelOp(_.basicAck(deliveryTag, multiple)) *> ZIO.succeed(MessageAcked(deliveryTag, multiple))

      override def basicConsume(queueName: String, consumer: RConsumer): ZIO[Any, IOException, ConsumerCreated] =
        channelOp(_.basicConsume(queueName, consumer)).map(ConsumerCreated(queueName, _))

      override def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[Any, IOException, MessageNacked] =
        channelOp(_.basicNack(deliveryTag, multiple, requeue)) *> ZIO.succeed(MessageNacked(deliveryTag, multiple, requeue))

      override def basicQos(prefetchCount: Int): ZIO[Any, IOException, QosEnabled] =
        channelOp(_.basicQos(prefetchCount)) *> ZIO.succeed(QosEnabled(prefetchCount))

      override def basicPublish(
        exchange: String,
        routingKey: String,
        body: Array[Byte],
        confirmed: Promise[IOException, Unit]
      ): ZIO[Any, IOException, MessagePublished] =
        (getNextPublishSeqNo <* channelOp(_.basicPublish(exchange, routingKey, emptyProps, body))) map (
          MessagePublished(
            _,
            confirmed
          )
        )

      override def addConfirmListener(listener: ConfirmListener): ZIO[Any, Nothing, ConfirmListenerAdded] =
        channelOp(_.addConfirmListener(listener)).orDie *> ZIO.succeed(ConfirmListenerAdded(listener))

      override def addShutdownListener(listener: ShutdownListener): ZIO[Any, Nothing, ShutdownListenerAdded] =
        channelOp(_.addShutdownListener(listener)).orDie *> ZIO.succeed(ShutdownListenerAdded(listener))

      override def confirmSelect: ZIO[Any, IOException, ConfirmSelectEnabled.type] =
        channelOp(_.confirmSelect()) *> ZIO.succeed(ConfirmSelectEnabled)

      def getNextPublishSeqNo: UIO[Long] =
        channelOp(_.getNextPublishSeqNo).orDie
    }
  }
}
