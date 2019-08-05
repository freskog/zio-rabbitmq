package freskog.effects.infra.rabbitmq

import java.io.IOException

import com.rabbitmq.client.{ BuiltinExchangeType, ConfirmListener, ShutdownListener, Consumer => RConsumer }
import freskog.effects.infra.rabbitmq.observer._
import zio.{ Promise, ZIO, ZManaged }

package object admin extends AdminClient.Service[AdminClient] {

  val adminClientService: ZIO[AdminClient, Nothing, AdminClient.Service[Any]] =
    ZIO.access(_.adminClient)

  def createAdminClient(name: String): ZManaged[ClientProvider, IOException, AdminClient] =
    ZManaged.unwrap(ZIO.access[ClientProvider](_.adminClientProvider.createAdminClient(name)))

  def exchangeDeclare(name: String, `type`: BuiltinExchangeType): ZIO[AdminClient, IOException, ExchangeDeclared] =
    adminClientService >>= (_.exchangeDeclare(name, `type`))

  def queueDeclare(name: String): ZIO[AdminClient, IOException, QueueDeclared] =
    adminClientService >>= (_ queueDeclare name)

  def queueBind(queue: String, exchange: String, routingKey: String): ZIO[AdminClient, IOException, QueueBoundToExchange] =
    adminClientService >>= (_.queueBind(queue, exchange, routingKey))

  def basicAck(deliveryTag: Long, multiple: Boolean): ZIO[AdminClient, IOException, MessageAcked] =
    adminClientService >>= (_.basicAck(deliveryTag, multiple))

  def basicConsume(queueName: String, consumer: RConsumer): ZIO[AdminClient, IOException, ConsumerCreated] =
    adminClientService >>= (_.basicConsume(queueName, consumer))

  def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[AdminClient, IOException, MessageNacked] =
    adminClientService >>= (_.basicNack(deliveryTag, multiple, requeue))

  def basicQos(prefetchCount: Int): ZIO[AdminClient, IOException, QosEnabled] = {
    adminClientService >>= (_ basicQos prefetchCount)
  }

  def basicPublish(
    exchange: String,
    routingKey: String,
    body: Array[Byte],
    confirmed: Promise[IOException, Unit]
  ): ZIO[AdminClient, IOException, MessagePublished] =
    adminClientService >>= (_.basicPublish(exchange, routingKey, body, confirmed))

  def addConfirmListener(listener: ConfirmListener): ZIO[AdminClient, Nothing, ConfirmListenerAdded] =
    adminClientService >>= (_ addConfirmListener listener)

  def addShutdownListener(listener: ShutdownListener): ZIO[AdminClient, Nothing, ShutdownListenerAdded] =
    adminClientService >>= (_ addShutdownListener listener)

  def confirmSelect: ZIO[AdminClient, IOException, ConfirmSelectEnabled.type] =
    adminClientService >>= (_.confirmSelect)

}
