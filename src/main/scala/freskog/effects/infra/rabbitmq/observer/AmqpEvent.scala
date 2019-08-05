package freskog.effects.infra.rabbitmq.observer

import java.io.IOException

import com.rabbitmq.client.{ ConfirmListener, ShutdownListener, ShutdownSignalException }
import zio.{ Promise, ZIO }

sealed abstract class AmqpEvent
case class ExchangeDeclared(name: String, `type`: String)                                                          extends AmqpEvent
case class QueueDeclared(name: String)                                                                             extends AmqpEvent
case class ConsumerCreated(queueName: String, consumerName: String)                                                extends AmqpEvent
case class MessageQueued(exchange: String, routingKey: String, msg: String, confirmed: Promise[IOException, Unit]) extends AmqpEvent
case class MessagePublished(seqNo: Long, confirmed: Promise[IOException, Unit])                                    extends AmqpEvent
case class QueueBoundToExchange(queue: String, exchange: String, routingKey: String)                               extends AmqpEvent
case class MessageReceived(payload: String, ack: ZIO[Any, Nothing, Unit], nack: ZIO[Any, Nothing, Unit])           extends AmqpEvent
case class MessageAcked(deliveryTag: Long, multiple: Boolean)                                                      extends AmqpEvent
case class MessageNacked(deliveryTag: Long, multiple: Boolean, requeue: Boolean)                                   extends AmqpEvent
case class QosEnabled(prefetchCount: Int)                                                                          extends AmqpEvent
case class ConfirmListenerAdded(listener: ConfirmListener)                                                         extends AmqpEvent
case class ShutdownListenerAdded(listener: ShutdownListener)                                                       extends AmqpEvent
case object ConfirmSelectEnabled                                                                                   extends AmqpEvent
case class SubscriberCancelledByUser(consumerTag: String)                                                          extends AmqpEvent
case class SubscriberCancelledByBroker(consumerTag: String)                                                        extends AmqpEvent
case class ConsumerShutdownReceived(name: String, reason: ShutdownSignalException)                                 extends AmqpEvent
case class PublisherShutdownReceived(name: String, reason: ShutdownSignalException)                                extends AmqpEvent
case class ConsumerStarted(consumerTag: String)                                                                    extends AmqpEvent
case class ConsumerRecovered(consumerTag: String)                                                                  extends AmqpEvent
case class AmqpError(e: Throwable)                                                                                 extends AmqpEvent
