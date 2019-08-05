package freskog.effects.infra.rabbitmq

import java.io.IOException

import freskog.effects.infra.rabbitmq.admin.AdminClient
import freskog.effects.infra.rabbitmq.observer.Observer
import zio.{ZIO, ZManaged}

package object topology extends TopologyClient.Service[TopologyClient] {

  val topologyClientService: ZIO[TopologyClient, Nothing, TopologyClient.Service[Any]] =
    ZIO.access[TopologyClient](_.topologyClient)

  val createTopologyClient: ZManaged[Observer with AdminClient, Nothing, TopologyClient] =
    TopologyClient.createTopologyClient

  def createTopology(declaredToplogy: Declaration): ZIO[TopologyClient, IOException, Unit] =
    topologyClientService >>= (_ createTopology declaredToplogy)
}
