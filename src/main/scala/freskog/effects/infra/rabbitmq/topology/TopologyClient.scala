package freskog.effects.infra.rabbitmq.topology

import java.io.IOException

import com.rabbitmq.client.BuiltinExchangeType
import freskog.effects.infra.rabbitmq.admin._
import freskog.effects.infra.rabbitmq.observer._
import zio.{ ZIO, ZManaged }

trait TopologyClient extends Serializable {
  val topologyClient: TopologyClient.Service[Any]
}

object TopologyClient extends Serializable { self =>
  trait Service[R] extends Serializable {
    def createTopology(topology: Declaration): ZIO[R, IOException, Unit]
  }

  val createTopologyClient: ZManaged[Observer with AdminClient, Nothing, TopologyClient] =
    ZManaged
      .environment[Observer with AdminClient]
      .map(
        env =>
          new TopologyClient {
            override val topologyClient: Service[Any] = new Service[Any] {
              override def createTopology(topology: Declaration): ZIO[Any, IOException, Unit] =
                self.createTopology(topology).provide(env)
            }
          }
      )

  def createTopology(declaredTopology: Declaration): ZIO[AdminClient with Observer, IOException, Unit] =
    for {
      _ <- ZIO.foreach(declaredTopology.queues.map(_.name))(queueDeclare(_) tap notifyOf)
      _ <- ZIO.foreach(declaredTopology.exchanges.map(_.name))(exchangeDeclare(_, BuiltinExchangeType.FANOUT) tap notifyOf)
      _ <- ZIO.foreach(declaredTopology.bindings) {
            case (AmqpQueue(queueName), FanoutExchange(exchangeName)) => queueBind(queueName, exchangeName, "") tap notifyOf
          }
    } yield ()
}
