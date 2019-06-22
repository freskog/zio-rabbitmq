package freskog.effects.infra.rabbitmq

import java.io.IOException

import scalaz.zio.ZIO

package object topology {

  val topologyClientService: ZIO[TopologyClient, Nothing, TopologyClient.Service[Any]] =
    ZIO.access[TopologyClient](_.topologyClient)

  def createTopology(declaredToplogy: Declaration): ZIO[TopologyClient, IOException, Unit] =
    ZIO.accessM[TopologyClient](_.topologyClient.createTopology(declaredToplogy))
}
