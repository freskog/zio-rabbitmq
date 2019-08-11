package freskog.effects.infra.rabbitmq

import java.io.IOException

import freskog.effects.infra.rabbitmq.admin._
import freskog.effects.infra.rabbitmq.observer._
import freskog.effects.infra.rabbitmq.topology._
import zio.ZManaged

object ClientFactory {

  def buildClientsFor(name: String): ZManaged[ClientProvider with Observer, IOException, AdminClient with TopologyClient with Observer] =
    for {
      observerEnv <- ZManaged.environment[Observer]
      adminEnv    <- createAdminClient(name)
      topologyEnv <- buildTopologyClient(observerEnv, adminEnv)
    } yield new AdminClient with TopologyClient with Observer {
      override val adminClient: AdminClient.Service[Any]       = adminEnv.adminClient
      override val topologyClient: TopologyClient.Service[Any] = topologyEnv.topologyClient
      override val observer: Observer.Service[Any]             = observerEnv.observer
    }

  def buildTopologyClient(observerEnv: Observer, adminEnv: AdminClient): ZManaged[Any, Nothing, TopologyClient] = {
    createTopologyClient.provide(new Observer with AdminClient {
      override val adminClient: AdminClient.Service[Any] = adminEnv.adminClient
      override val observer: Observer.Service[Any] = observerEnv.observer
    })
  }
}
