package freskog.effects.infra.rabbitmq.topology

import java.io.IOException

import com.rabbitmq.client.{BuiltinExchangeType, ConnectionFactory}
import freskog.effects.infra.rabbitmq.admin._
import freskog.effects.infra.rabbitmq.events._
import scalaz.zio.{ZIO, ZManaged}

trait TopologyClient extends Serializable {
  val topologyClient: TopologyClient.Service
}

object TopologyClient extends Serializable { self =>
  trait Service extends Serializable {
    def createTopology(decl: Declaration): ZIO[Any, IOException, Unit]
  }

  def makeLiveTopologyClient(cf: ConnectionFactory, name: String): ZManaged[Any, IOException, TopologyClient] =
    (AdminClient.makeLiveAdminClient(cf, name) zipWith Events.makeEvents.toManaged_)(makeLiveTopologyClientFrom)

  def makeLiveTopologyClientFrom(adminEnv: AdminClient, eventsEnv: Events):TopologyClient =
    new TopologyClient {
      override val topologyClient: Service = (decl: Declaration) =>
        createTopology(decl).provide {
          new AdminClient with Events {
            override val adminClient: AdminClient.Service = adminEnv.adminClient
            override val events: Events.Service = eventsEnv.events
          }
        }
    }

  def createTopology(declaredTopology: Declaration): ZIO[AdminClient with Events, IOException, Unit] =
    for {
      _ <- ZIO.foreach(declaredTopology.queues.map(_.name))(queueDeclare(_) tap publish)
      _ <- ZIO.foreach(declaredTopology.exchanges.map(_.name))(exchangeDeclare(_, BuiltinExchangeType.FANOUT) tap publish)
      _ <- ZIO.foreach(declaredTopology.bindings) {
            case (AmqpQueue(queueName), FanoutExchange(exchangeName)) => queueBind(queueName, exchangeName, "") tap publish
          }
    } yield ()
}
