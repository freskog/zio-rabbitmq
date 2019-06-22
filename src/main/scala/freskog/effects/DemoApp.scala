package freskog.effects

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.app.services.commands._
import freskog.effects.app.services.results._
import freskog.effects.infra.api._
import scalaz.zio._

object DemoApp extends App {

  type LiveEnv = ResultHandler with CalculatorCommandHandler with InfraApi

  val commandConsumer: ZIO[InfraApi with CalculatorCommandHandler, Nothing, Unit] =
    handleCalculatorCommand(processCommand)

  val resultConsumer: ZIO[InfraApi with ResultHandler, Nothing, Unit] =
    handleResultEvent(processResult)

  val program: ZIO[LiveEnv, Nothing, Unit] =
    commandConsumer zipParRight resultConsumer

  val defaultConnectionFactory: UIO[ConnectionFactory] =
    ZIO
      .effectTotal(new ConnectionFactory)
      .tap(disableAutorecovery)
      .tap(disableTopologyRecovery)

  def liveEnv(cf: ConnectionFactory): ZIO[Any, Nothing, LiveEnv] =
    for {
      api        <- InfraApi.makeLiveInfraApi(cf)
      cmdHandler <- CalculatorCommandHandler.makeLiveCalculatorCommandHandler(api)
    } yield new CalculatorCommandHandler with ResultHandler.Live with InfraApi {
      override val calculatorCommandHandler: CalculatorCommandHandler.Service[Any] = cmdHandler.calculatorCommandHandler
      override val infraApi: InfraApi.Service[Any]                                 = api.infraApi
    }

  def disableAutorecovery(cf: ConnectionFactory): UIO[Unit] =
    ZIO.effectTotal(cf.setAutomaticRecoveryEnabled(false))

  def disableTopologyRecovery(cf: ConnectionFactory): UIO[Unit] =
    ZIO.effectTotal(cf.setTopologyRecoveryEnabled(false))

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    (defaultConnectionFactory >>= liveEnv >>= program.provide) *> UIO.succeed(0)

}
