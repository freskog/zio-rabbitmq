package freskog.effects.rabbitmq.events

import com.rabbitmq.client.{ConfirmListener, ShutdownListener, ShutdownSignalException, Consumer => RConsumer}
import scalaz.zio.Promise

sealed abstract class AmqpEvent
case class ExchangeDeclared(name:String, `type`:String) extends AmqpEvent
case class QueueDeclared(name:String) extends AmqpEvent
case class PublishSeqNoGenerated(seqNo:Long) extends AmqpEvent
case class ConsumerCreated(queueName:String, consumerName:String, consumer:RConsumer) extends AmqpEvent
case class MessagePublished(exchange:String, routingKey:String, msg:String) extends AmqpEvent
case class QueueBoundToExchange(queue:String, exchange:String, routingKey:String) extends AmqpEvent
case class MessageReceived(seqNo:Long, redelivered:Boolean, payload:String) extends AmqpEvent
case class MessageAcked(deliveryTag:Long, multiple:Boolean) extends AmqpEvent
case class MessageNacked(deliveryTag:Long, multiple:Boolean, requeue:Boolean) extends AmqpEvent
case class QosEnabled(prefetchCount:Int) extends AmqpEvent
case class ConfirmListenerAdded(listener:ConfirmListener) extends AmqpEvent
case class ShutdownListenerAdded(listener:ShutdownListener) extends AmqpEvent
case object ConfirmSelectEnabled extends AmqpEvent
case class SubscriberCancelledByUser(consumerTag:String, stopped:Promise[Nothing, Unit]) extends AmqpEvent
case class SubscriberCancelledByBroker(consumerTag:String, stopped:Promise[Nothing, Unit]) extends AmqpEvent
case class ConsumerShutdownReceived(name:String, reason:ShutdownSignalException, stopped:Promise[Nothing, Unit]) extends AmqpEvent
case class PublisherShutdownReceived(name:String, reason:ShutdownSignalException) extends AmqpEvent
case class ConsumerStarted(consumerTag:String) extends AmqpEvent
case class ConsumerRecovered(consumerTag:String) extends AmqpEvent