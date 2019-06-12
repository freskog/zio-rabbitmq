package freskog.effects.rabbitmq.admin

import java.io.IOException

import com.rabbitmq.client.AMQP.Confirm
import com.rabbitmq.client.BuiltinExchangeType.FANOUT
import com.rabbitmq.client.{Channel, ConfirmListener, Connection, ConnectionFactory, GetResponse, ShutdownListener, Consumer => RConsumer}
import freskog.effects.rabbitmq._
import freskog.effects.rabbitmq.consumer.Consumer.untilNonNull
import freskog.effects.rabbitmq.publisher.Publisher.{emptyProps, noKey}
import scalaz.zio._
import scalaz.zio.blocking._
import scalaz.zio.clock._

trait AdminClient extends Serializable {
  val adminClient: AdminClient.Service[Any]
}

object AdminClient extends Serializable {
  self =>

  trait Service[R] extends Serializable {

    def connectionFactory: ZManaged[Any, Nothing, ConnectionFactory]

    def createManagedConnection(name: String): ZManaged[Any, IOException, Connection]

    def createManagedChannel(connectionName: String): ZManaged[Any, IOException, Channel]

    def declareFanoutExchange(channel: Channel, name: String): ZIO[Any, IOException, FanoutExchange]

    def declareQueue(channel: Channel, name: String): ZIO[Any, IOException, AmqpQueue]

    def bindQueueToFanout(channel: Channel, queue: AmqpQueue, exchangeName: FanoutExchange): ZIO[Any, IOException, Unit]

    def basicGet(channel: Channel, queueName:String): ZIO[Any, IOException, GetResponse]

    def basicAck(channel: Channel, deliveryTag: Long, multiple: Boolean): ZIO[Any, IOException, Unit]

    def basicConsume(channel: Channel, queue: String, autoAck: Boolean, callback: RConsumer): ZIO[Any, IOException, String]

    def basicNack(channel: Channel, deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[Any, IOException, Unit]

    def basicQos(channel: Channel, prefetchSize: Int, prefetchCount: Int, global: Boolean): ZIO[Any, IOException, Unit]

    def basicPublish(chan: Channel, ex: String, body: Array[Byte]): ZIO[Any, IOException, Unit]

    def addConfirmListener(channel: Channel, listener: ConfirmListener): ZIO[Any, Nothing, Unit]

    def addShutdownListener(channel:Channel, listener: ShutdownListener):ZIO[Any, Nothing, Unit]

    def confirmSelect(channel: Channel): ZIO[Any, IOException, Confirm.SelectOk]

    def getNextPublishSeqNo(channel: Channel): UIO[Long]

  }

  val defaultConnectionFactory: UIO[ConnectionFactory] =
    UIO.effectTotal {
      val cf = new ConnectionFactory
      cf.setAutomaticRecoveryEnabled(false)
      cf.setTopologyRecoveryEnabled(false)
      cf
    }

  trait Live extends AdminClient with Blocking.Live with Clock.Live { env =>
    override val adminClient: Service[Any] =
      new Service[Any] {

        override def connectionFactory: ZManaged[Any, Nothing, ConnectionFactory] =
          self.connectionFactory

        override def createManagedConnection(name: String): ZManaged[Any, IOException, Connection] =
          self.createManagedConnection(name)

        override def createManagedChannel(connectionName: String): ZManaged[Any, IOException, Channel] =
          self.createManagedChannel(connectionName)

        override def declareFanoutExchange(channel: Channel, name: String): ZIO[Any, IOException, FanoutExchange] =
          self.declareFanoutExchange(channel, name).provide(env)

        override def declareQueue(channel: Channel, name: String): ZIO[Any, IOException, AmqpQueue] =
          self.declareQueue(channel, name).provide(env)

        override def bindQueueToFanout(channel: Channel, queue: AmqpQueue, fanout: FanoutExchange): ZIO[Any, IOException, Unit] =
          self.bindQueueToFanout(channel, queue, fanout).provide(env)

        override def basicGet(channel: Channel, queueName: String): ZIO[Any, IOException, GetResponse] =
          self.basicGet(channel, queueName).provide(env)

        override def basicAck(channel: Channel, deliveryTag: Long, multiple: Boolean): ZIO[Any, IOException, Unit] =
          self.basicAck(channel, deliveryTag, multiple).provide(env)

        override def basicConsume(channel: Channel, queue: String, autoAck: Boolean, callback: RConsumer): ZIO[Any, IOException, String] =
          self.basicConsume(channel, queue, autoAck, callback).provide(env)

        override def basicNack(channel: Channel, deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[Any, IOException, Unit] =
          self.basicNack(channel, deliveryTag, multiple, requeue).provide(env)

        override def basicQos(channel: Channel, prefetchSize: Int, prefetchCount: Int, global: Boolean): ZIO[Any, IOException, Unit] =
          self.basicQos(channel, prefetchSize, prefetchCount, global).provide(env)

        override def basicPublish(chan: Channel, ex: String, body: Array[Byte]): ZIO[Any, IOException, Unit] =
          self.basicPublish(chan, ex, body).provide(env)

        override def addConfirmListener(channel: Channel, listener: ConfirmListener): ZIO[Any, Nothing, Unit] =
          self.addConfirmListener(channel, listener).provide(env)

        override def addShutdownListener(channel:Channel, listener: ShutdownListener):ZIO[Any, Nothing, Unit] =
          self.addShutdownListener(channel, listener)

        override def confirmSelect(channel: Channel): ZIO[Any, IOException, Confirm.SelectOk] =
          self.confirmSelect(channel).provide(env)

        override def getNextPublishSeqNo(channel: Channel): UIO[Long] =
          self.getNextPublishSeqNo(channel).provide(env)
      }
  }

  import scala.collection.JavaConverters._

  val connectionFactory: ZManaged[Any, Nothing, ConnectionFactory] =
    defaultConnectionFactory.toManaged_

  def createManagedConnection(name: String): ZManaged[Any, IOException, Connection] =
    connectionFactory >>= (cf => ZManaged.make(newConnection(name, cf))(closeConnection).refineOrDie(convertToIOException))

  def createManagedChannel(name: String): ZManaged[Any, IOException, Channel] =
    createManagedConnection(name).flatMap(
      conn => ZManaged.make(createChannel(conn))(closeChannel).refineOrDie(convertToIOException)
    )

  def closeChannel(chan: Channel): UIO[Unit] =
    ZIO.effect(chan.close()).catchAll(printErrMsg)

  def createChannel(conn: Connection): Task[Channel] =
    ZIO.effect(conn.createChannel())

  def closeConnection(conn: Connection): UIO[Unit] =
    ZIO.effect(conn.close()).catchAll(printErrMsg)

  def newConnection(name: String, connectionFactory: ConnectionFactory): Task[Connection] =
    ZIO.effect(connectionFactory.newConnection(name))


  def declareFanoutExchange(channel: Channel, name: String): ZIO[Any, IOException, FanoutExchange] =
    ZIO.effect(channel.exchangeDeclare(name, FANOUT)).refineOrDie(convertToIOException) *> ZIO.succeed(FanoutExchange(name))

  def declareQueue(channel: Channel, name: String): ZIO[Any, IOException, AmqpQueue] =
    ZIO.effect(channel.queueDeclare(name, false, false, false, Map.empty[String,AnyRef].asJava))
      .refineOrDie(convertToIOException) *> ZIO.succeed(AmqpQueue(name))

  def bindQueueToFanout(channel: Channel, queue: AmqpQueue, fanout: FanoutExchange): ZIO[Any, IOException, Unit] =
    ZIO.effect(channel.queueBind(queue.name, fanout.name, "")).refineOrDie(convertToIOException).unit

  def basicGet(channel: Channel, queueName:String): ZIO[Blocking with Clock, IOException, GetResponse] =
    effectBlocking(channel.basicGet(queueName, false)).refineOrDie(convertToIOException).repeat(untilNonNull)

  def basicAck(channel: Channel, deliveryTag: Long, multiple: Boolean): ZIO[Any, IOException, Unit] =
    ZIO.effect(channel.basicAck(deliveryTag, multiple)).refineOrDie(convertToIOException)

  def basicConsume(channel: Channel, queue: String, autoAck: Boolean, callback: RConsumer): ZIO[Any, IOException, String] =
    ZIO.effect(channel.basicConsume(queue, autoAck, callback)).refineOrDie(convertToIOException)

  def basicNack(channel: Channel, deliveryTag: Long, multiple: Boolean, requeue: Boolean): ZIO[Any, IOException, Unit] =
    ZIO.effect(channel.basicNack(deliveryTag, multiple, requeue)).refineOrDie(convertToIOException)

  def basicQos(channel: Channel, prefetchSize: Int, prefetchCount: Int, global: Boolean): ZIO[Any, IOException, Unit] =
    ZIO.effect(channel.basicQos(prefetchSize, prefetchCount, global)).refineOrDie(convertToIOException)

  def basicPublish(chan: Channel, ex: String, body: Array[Byte]): ZIO[Any, IOException, Unit] =
    ZIO.effect(chan.basicPublish(ex, noKey, emptyProps, body)).refineOrDie(convertToIOException)

  def addConfirmListener(channel: Channel, listener: ConfirmListener): ZIO[Any, Nothing, Unit] =
    ZIO.effectTotal(channel.addConfirmListener(listener))

  def addShutdownListener(channel:Channel, listener:ShutdownListener): ZIO[Any, Nothing, Unit] =
    ZIO.effectTotal(channel.addShutdownListener(listener))

  def confirmSelect(channel: Channel): ZIO[Any, IOException, Confirm.SelectOk] =
    ZIO.effect(channel.confirmSelect()).refineOrDie(convertToIOException)

  def getNextPublishSeqNo(channel: Channel): UIO[Long] =
    ZIO.effectTotal(channel.getNextPublishSeqNo)
}
