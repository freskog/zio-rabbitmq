package freskog.effects.infra.rabbitmq.consumer

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.infra.rabbitmq.topology._
import scalaz.zio._

trait Consumer extends Serializable {
  val consumer: Consumer.Service[Any]
}

object Consumer extends Serializable {

  trait Service[R] extends Serializable {
    def consumeUsing[R1, E](userFunction: String => ZIO[R1, E, Unit]): ZIO[R1, E, Unit]
  }

  def makeCallbackConsumer(cf: ConnectionFactory, queueName: String, topology: Declaration): UIO[Consumer] =
    ZIO.effectTotal {
      new Consumer {
        override val consumer: Service[Any] =
          new Service[Any] {
            override def consumeUsing[R1, E](userFunction: String => ZIO[R1, E, Unit]): ZIO[R1, E, Unit] =
              LiveConsumer.consumeWith[R1, E](cf, queueName, topology, LiveConsumer.initializeConsumerOn(queueName), userFunction)
          }
      }
    }

  def makePollingConsumer(cf: ConnectionFactory, queueName: String, topology: Declaration): UIO[Consumer] =
    ZIO.effectTotal {
      new Consumer {
        override val consumer: Service[Any] =
          new Service[Any] {
            override def consumeUsing[R1, E](userFunction: String => ZIO[R1, E, Unit]): ZIO[R1, E, Unit] =
              LiveConsumer.consumeWith[R1, E](cf, queueName, topology, LiveConsumer.pollForNewMessages(queueName), userFunction)
          }
      }
    }

}
