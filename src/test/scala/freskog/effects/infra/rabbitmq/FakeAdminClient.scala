package freskog.effects.infra.rabbitmq

import java.io.IOException

import com.rabbitmq.client._
import freskog.effects.infra.rabbitmq.admin._
import freskog.effects.infra.rabbitmq.events._
import scalaz.zio._

trait FakeAdminClient extends AdminClient {

  val messageQueue: Queue[Option[MessageReceived]]

  val seqNo: Ref[Long]

  private val emptyProps = new AMQP.BasicProperties.Builder().build()
  private val none       = ""

  override val adminClient: AdminClient.Service[Any] =
    new AdminClient.Service[Any] {

      override def exchangeDeclare(name: String, `type`: BuiltinExchangeType): ZIO[Any, IOException, ExchangeDeclared] =
        ZIO.succeed(ExchangeDeclared(name, `type`.getType))

      override def queueDeclare(name: String): ZIO[Any, IOException, QueueDeclared] =
        ZIO.succeed(QueueDeclared(name))

      override def queueBind(queue: String, exchange: String, routingKey: String): ZIO[Any, IOException, QueueBoundToExchange] =
        ZIO.succeed(QueueBoundToExchange(queue, exchange, routingKey))

      override def basicAck(deliveryTag: Long, multiple: Boolean): ZIO[Any, IOException, MessageAcked] =
        ZIO.succeed(MessageAcked(deliveryTag, multiple))

      override def basicConsume(queueName: String, consumer: Consumer): ZIO[Any, IOException, ConsumerCreated] =
        ZIO.succeed(ConsumerCreated(queueName, "fakename.com", consumer)) <* (messageQueue.take >>= {
          case None => ZIO.unit
          case Some(MessageReceived(tag, redelivered, payload)) =>
            ZIO.effectTotal(
              consumer.handleDelivery("fakename.com", new Envelope(tag, redelivered, none, none), emptyProps, payload.getBytes())
            )
        }).forever.fork

      override def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[Any, IOException, MessageNacked] =
        ZIO.succeed(MessageNacked(deliveryTag, multiple, requeue))

      override def basicQos(prefetchCount: Int): ZIO[Any, IOException, QosEnabled] =
        ZIO.succeed(QosEnabled(prefetchCount))

      override def basicPublish(exchange: String, routingKey: String, body: Array[Byte]): ZIO[Any, IOException, MessagePublished] =
        new String(body, "UTF-8") match {
          case "fake-an-error-on-publish" => ZIO.fail(new IOException("Fake error when simulating talking to broker"))
          case payload: String            => ZIO.succeed(MessagePublished(exchange, routingKey, payload))
        }

      override def confirmSelect: ZIO[Any, IOException, ConfirmSelectEnabled.type] =
        ZIO.succeed(ConfirmSelectEnabled)

      override def addConfirmListener(listener: ConfirmListener): ZIO[Any, Nothing, ConfirmListenerAdded] =
        UIO.succeed(ConfirmListenerAdded(listener))

      override def addShutdownListener(listener: ShutdownListener): ZIO[Any, Nothing, ShutdownListenerAdded] =
        UIO.succeed(ShutdownListenerAdded(listener))

      override def basicGet(queueName: String): ZIO[Any, IOException, Option[MessageReceived]] =
        messageQueue.take

      override def getNextPublishSeqNo: ZIO[Any, Nothing, PublishSeqNoGenerated] =
        seqNo.modify(n => (PublishSeqNoGenerated(n), n + 1))
    }
}
