package freskog.effects.rabbitmq.topology

import java.io.IOException


import freskog.effects.rabbitmq.admin._
import scalaz.zio.ZIO

trait TopologyClient extends Serializable {
  val topologyClient:TopologyClient.Service[Any]
}

object TopologyClient extends Serializable { self =>
  trait Service[R] extends Serializable {
    def createTopology(declaredToplogy:Declaration):ZIO[Any, IOException, Unit]
  }

  trait Live extends TopologyClient with AdminClient { env =>
    override val topologyClient: Service[Any] = self.createTopology(_).provide(env)
  }

  def createTopology(declaredTopology:Declaration): ZIO[AdminClient, IOException, Unit] =
    createManagedChannel("Topology").use { channel =>
      for {
       _ <- ZIO.foreach(declaredTopology.queues.map(_.name))(declareQueue(channel, _))
       _ <- ZIO.foreach(declaredTopology.exchanges.map(_.name))(declareFanoutExchange(channel, _))
       _ <- ZIO.foreach(declaredTopology.bindings)(binding => bindQueueToFanout(channel, binding._1, binding._2))
      } yield ()
    }
}


