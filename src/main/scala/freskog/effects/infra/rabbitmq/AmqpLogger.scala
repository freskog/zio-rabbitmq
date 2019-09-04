package freskog.effects.infra.rabbitmq

import freskog.effects.app.logger._
import freskog.effects.infra.rabbitmq.observer._
import zio.ZIO

object AmqpLogger {

  def logEvents(name: String): ZIO[Observer with Logger, Nothing, Unit] = handle {
    case e @ MessageQueued(_, _, _, _)       => debug(s"$name - $e")
    case e @ MessagePublished(_, _)          => debug(s"$name - $e")
    case e @ MessageReceived(_, _, _)        => debug(s"$name - $e")
    case e @ MessageAcked(_, _)              => debug(s"$name - $e")
    case e @ MessageNacked(_, _, _)          => warn(s"$name - $e")
    case e @ SubscriberCancelledByBroker(_)  => warn(s"$name - $e")
    case e @ ConsumerShutdownReceived(_, _)  => warn(s"$name - $e")
    case e @ PublisherShutdownReceived(_, _) => warn(s"$name - $e")
    case e @ AmqpError(_)                    => error(s"$name - $e")
    case e: AmqpEvent                        => info(s"$name - $e")
  }.fork.unit

}
