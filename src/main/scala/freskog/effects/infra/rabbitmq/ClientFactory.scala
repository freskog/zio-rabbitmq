package freskog.effects.infra.rabbitmq

import java.io.IOException

import freskog.effects.infra.rabbitmq.admin.{AdminClient, ClientProvider, createAdminClient}
import freskog.effects.infra.rabbitmq.observer.Observer
import freskog.effects.infra.rabbitmq.topology.{TopologyClient, createTopologyClient}
import zio.ZManaged

object ClientFactory {

  def buildClientsFor(name: String): ZManaged[ClientProvider with Observer, IOException, AdminClient with TopologyClient with Observer] =
    for {
      observerEnv <- ZManaged.environment[Observer]
      adminEnv    <- createAdminClient(name)
      topologyEnv <- createTopologyClient.provide(new Observer with AdminClient {
        override val adminClient: AdminClient.Service[Any] = adminEnv.adminClient
        override val observer: Observer.Service[Any]       = observerEnv.observer
      })
    } yield new AdminClient with TopologyClient with Observer {
      override val adminClient: AdminClient.Service[Any]       = adminEnv.adminClient
      override val topologyClient: TopologyClient.Service[Any] = topologyEnv.topologyClient
      override val observer: Observer.Service[Any]             = observerEnv.observer
    }
}
