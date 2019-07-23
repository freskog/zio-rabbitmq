package freskog.effects.infra.rabbitmq

import java.io.IOException

import com.rabbitmq.client.{ BuiltinExchangeType, ConfirmListener, ShutdownListener, Consumer => RConsumer }
import freskog.effects.infra.rabbitmq.observer._
import zio.ZIO

package object admin {

  val adminClientService: ZIO[AdminClient, Nothing, admin.AdminClient.Service] =
    ZIO.access(_.adminClient)

  def exchangeDeclare(name: String, `type`: BuiltinExchangeType): ZIO[AdminClient, IOException, ExchangeDeclared] =
    ZIO.accessM[AdminClient](_.adminClient.exchangeDeclare(name, `type`))

  def queueDeclare(name: String): ZIO[AdminClient, IOException, QueueDeclared] =
    ZIO.accessM[AdminClient](_.adminClient.queueDeclare(name))

  def queueBind(queue: String, exchange: String, routingKey: String): ZIO[AdminClient, IOException, QueueBoundToExchange] =
    ZIO.accessM[AdminClient](_.adminClient.queueBind(queue, exchange, routingKey))

  def basicGet(queueName: String): ZIO[AdminClient, IOException, Option[MessageReceived]] =
    ZIO.accessM[AdminClient](_.adminClient.basicGet(queueName))

  def basicAck(deliveryTag: Long, multiple: Boolean): ZIO[AdminClient, IOException, MessageAcked] =
    ZIO.accessM[AdminClient](_.adminClient.basicAck(deliveryTag, multiple))

  def basicConsume(queueName: String, consumer: RConsumer): ZIO[AdminClient, IOException, ConsumerCreated] =
    ZIO.accessM[AdminClient](_.adminClient.basicConsume(queueName, consumer))

  def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[AdminClient, IOException, MessageNacked] =
    ZIO.accessM[AdminClient](_.adminClient.basicNack(deliveryTag, multiple, requeue))

  def basicQos(prefetchCount: Int): ZIO[AdminClient, IOException, QosEnabled] =
    ZIO.accessM[AdminClient](_.adminClient.basicQos(prefetchCount))

  def basicPublish(exchange: String, routingKey: String, body: Array[Byte]): ZIO[AdminClient, IOException, MessagePublished] =
    ZIO.accessM[AdminClient](_.adminClient.basicPublish(exchange, routingKey, body))

  def addConfirmListener(listener: ConfirmListener): ZIO[AdminClient, Nothing, ConfirmListenerAdded] =
    ZIO.accessM[AdminClient](_.adminClient.addConfirmListener(listener))

  def addShutdownListener(listener: ShutdownListener): ZIO[AdminClient, Nothing, ShutdownListenerAdded] =
    ZIO.accessM[AdminClient](_.adminClient.addShutdownListener(listener))

  def confirmSelect: ZIO[AdminClient, IOException, ConfirmSelectEnabled.type] =
    ZIO.accessM[AdminClient](_.adminClient.confirmSelect)

  def getNextPublishSeqNo: ZIO[AdminClient, Nothing, PublishSeqNoGenerated] =
    ZIO.accessM[AdminClient](_.adminClient.getNextPublishSeqNo)

}
