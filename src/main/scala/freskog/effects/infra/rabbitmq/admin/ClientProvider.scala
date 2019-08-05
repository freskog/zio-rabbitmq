package freskog.effects.infra.rabbitmq.admin

import java.io.IOException

import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}
import freskog.effects.app.logger._
import freskog.effects.infra.rabbitmq.admin.AdminClient.{Live, convertToIOException}
import zio.blocking.Blocking
import zio.{ZIO, ZManaged}

trait ClientProvider {
  val adminClientProvider: ClientProvider.Provider[Any]
}

object ClientProvider {
  trait Provider[R] {
    def createAdminClient(name: String): ZManaged[R, IOException, AdminClient]
  }

  def createLiveAdminClientProvider(cf:ConnectionFactory): ZManaged[Logger with Blocking, Nothing, ClientProvider] =
    ZManaged.environment[Logger with Blocking] map { loggerEnv =>
      new ClientProvider {
        override val adminClientProvider: Provider[Any] =
          (name: String) => createLiveAdminClient(name, cf).provide(loggerEnv)
      }
    }

  def createLiveAdminClient(name: String, cf:ConnectionFactory): ZManaged[Blocking with Logger, IOException, AdminClient] =
    for {
      conn <- createManagedConnection(name, cf)
      chan <- createManagedChannel(conn)
      blockingEnv <- ZManaged.environment[Blocking]
    } yield new Live with Blocking {
      override val channel: Channel = chan
      override val blocking: Blocking.Service[Any] = blockingEnv.blocking
    }

  def createManagedConnection(name: String, cf:ConnectionFactory): ZManaged[Logger, IOException, Connection] =
    ZManaged.make(newConnection(name, cf))(closeConnection)

  def createManagedChannel(conn: Connection): ZManaged[Logger, IOException, Channel] =
    ZManaged.make(createChannel(conn))(closeChannel)

  def closeChannel(chan: Channel): ZIO[Logger, Nothing, Unit] =
    ZIO.effect(chan.close()).catchAll(throwable)

  def createChannel(conn: Connection): ZIO[Logger, IOException, Channel] =
    ZIO.effect(conn.createChannel()).refineOrDie(convertToIOException)

  def closeConnection(conn: Connection): ZIO[Logger, Nothing, Unit] =
    ZIO.effect(conn.close()).catchAll(throwable)

  def newConnection(name: String, connectionFactory: ConnectionFactory): ZIO[Logger, IOException, Connection] =
    ZIO.effect(connectionFactory.newConnection(name)).refineOrDie(convertToIOException)

}
