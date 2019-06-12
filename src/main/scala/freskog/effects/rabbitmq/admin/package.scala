package freskog.effects.rabbitmq

import java.io.IOException

import com.rabbitmq.client.AMQP.Confirm
import com.rabbitmq.client.{Channel, ConfirmListener, Connection, ConnectionFactory, GetResponse, ShutdownListener, Consumer => RConsumer}
import scalaz.zio.{ZIO, ZManaged}

package object admin {

  val adminClientService: ZIO[AdminClient, Nothing, admin.AdminClient.Service[Any]] =
    ZIO.access(_.adminClient)

  def connectionFactory: ZManaged[AdminClient, Nothing, ConnectionFactory] =
    ZManaged.unwrap(ZIO.access[AdminClient](_.adminClient.connectionFactory))

  def createManagedConnection(name: String): ZManaged[AdminClient, IOException, Connection] =
    ZManaged.unwrap(ZIO.access[AdminClient](_.adminClient.createManagedConnection(name)))

  def createManagedChannel(connectionName: String): ZManaged[AdminClient, IOException, Channel] =
    ZManaged.unwrap(ZIO.access[AdminClient](_.adminClient.createManagedChannel(connectionName)))

  def declareFanoutExchange(channel: Channel, name: String): ZIO[AdminClient, IOException, FanoutExchange] =
    ZIO.accessM(_.adminClient.declareFanoutExchange(channel, name))

  def declareQueue(channel: Channel, name: String): ZIO[AdminClient, IOException, AmqpQueue] =
    ZIO.accessM(_.adminClient.declareQueue(channel, name))

  def bindQueueToFanout(channel: Channel, queue: AmqpQueue, fanout: FanoutExchange): ZIO[AdminClient, IOException, Unit] =
    ZIO.accessM(_.adminClient.bindQueueToFanout(channel, queue, fanout))

  def basicGet(channel: Channel, queueName: String): ZIO[AdminClient, IOException, GetResponse] =
    ZIO.accessM[AdminClient](_.adminClient.basicGet(channel, queueName))

  def basicAck(channel: Channel, deliveryTag: Long, multiple: Boolean): ZIO[AdminClient, IOException, Unit] =
    ZIO.accessM[AdminClient](_.adminClient.basicAck(channel, deliveryTag, multiple))

  def basicConsume(channel: Channel, queue: String, autoAck: Boolean, callback: RConsumer): ZIO[AdminClient, IOException, String] =
    ZIO.accessM[AdminClient](_.adminClient.basicConsume(channel, queue, autoAck, callback))

  def basicNack(channel: Channel, deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[AdminClient, IOException, Unit] =
    ZIO.accessM[AdminClient](_.adminClient.basicNack(channel, deliveryTag, multiple, requeue))

  def basicQos(channel: Channel, prefetchSize: Int, prefetchCount: Int, global: Boolean): ZIO[AdminClient, IOException, Unit] =
    ZIO.accessM[AdminClient](_.adminClient.basicQos(channel, prefetchSize, prefetchCount, global))

  def basicPublish(chan: Channel, ex: String, body: Array[Byte]): ZIO[AdminClient, IOException, Unit] =
    ZIO.accessM[AdminClient](_.adminClient.basicPublish(chan, ex, body))

  def addConfirmListener(channel: Channel, listener: ConfirmListener): ZIO[AdminClient, Nothing, Unit] =
    ZIO.accessM[AdminClient](_.adminClient.addConfirmListener(channel, listener))

  def addShutdownListener(channel:Channel, listener: ShutdownListener):ZIO[AdminClient, Nothing, Unit] =
    ZIO.accessM[AdminClient](_.adminClient.addShutdownListener(channel, listener))

  def confirmSelect(channel: Channel): ZIO[AdminClient, IOException, Confirm.SelectOk] =
    ZIO.accessM[AdminClient](_.adminClient.confirmSelect(channel))

  def getNextPublishSeqNo(channel: Channel): ZIO[AdminClient, Nothing, Long] =
    ZIO.accessM[AdminClient](_.adminClient.getNextPublishSeqNo(channel))

}
