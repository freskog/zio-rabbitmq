package freskog.effects

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.app.services.commands._
import freskog.effects.app.services.results._
import freskog.effects.infra.api._
import zio._

object DemoApp extends App {

  val commandConsumer: ZIO[InfraApi with CalculatorCommandHandler, Nothing, Unit] =
    ZIO.environment[CalculatorCommandHandler].flatMap { env =>
      handleCalculatorCommand(processCommand(_).provide(env))
    }

  val resultConsumer: ZIO[InfraApi with ResultHandler, Nothing, Unit] =
    ZIO.environment[ResultHandler] >>= { env =>
      handleResultEvent(processResult(_).provide(env))
    }
  val program: ZIO[InfraApi with CalculatorCommandHandler with ResultHandler, Nothing, Unit] =
    commandConsumer zipParRight resultConsumer

  val defaultConnectionFactory: ZManaged[Any, Nothing, ConnectionFactory] =
    ZManaged.fromEffect(
      ZIO
        .effectTotal(new ConnectionFactory)
        .tap(disableAutorecovery)
        .tap(disableTopologyRecovery)
    )

  def disableAutorecovery(cf: ConnectionFactory): UIO[Unit] =
    ZIO.effectTotal(cf.setAutomaticRecoveryEnabled(false))

  def disableTopologyRecovery(cf: ConnectionFactory): UIO[Unit] =
    ZIO.effectTotal(cf.setTopologyRecoveryEnabled(false))

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    (defaultConnectionFactory >>= DemoEnv.liveEnv).use(program.provide) *> UIO.succeed(0)

}
