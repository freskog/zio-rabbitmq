package freskog.effects

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.app.logger.Logger
import freskog.effects.app.services.commands.{ CalculatorCommandHandler, ResultPublisher }
import freskog.effects.app.services.results.ResultHandler
import freskog.effects.domain.calculator.Calculator
import freskog.effects.domain.formatter.ResultFormatter
import freskog.effects.infra.api.InfraApi
import freskog.effects.infra.logger.LiveLogger
import freskog.effects.infra.rabbitmq.admin.ClientProvider
import zio.ZManaged
import zio.blocking.Blocking
import zio.clock.Clock

object DemoEnv {
  def liveEnv(cf: ConnectionFactory): ZManaged[Clock with Blocking, Nothing, ResultHandler with CalculatorCommandHandler with InfraApi] =
    for {
      loggerEnv     <- ZManaged.fromEffect(LiveLogger.makeLogger("APP"))
      blockingEnv   <- ZManaged.environment[Blocking]
      clockEnv      <- ZManaged.environment[Clock]
      calculatorEnv <- ZManaged.succeed(Calculator.createCalculator)
      formatterEnv  <- ZManaged.succeed(ResultFormatter.createResultFormatter)
      clientP       <- buildClientProvider(cf, loggerEnv, blockingEnv)
      api           <- buildInfraApi(loggerEnv, blockingEnv, clockEnv, clientP)
      publishResult = ResultPublisher.fromPublishFn(api.infraApi.publishResultEvent)
      cmdHandler    <- buildCalculatorCmdHandler(calculatorEnv, publishResult)
      resultHandler <- buildResultHandler(loggerEnv, formatterEnv)
    } yield new ResultHandler with CalculatorCommandHandler with InfraApi {
      override val resultEventHandler: ResultHandler.Service[Any]                  = resultHandler.resultEventHandler
      override val calculatorCommandHandler: CalculatorCommandHandler.Service[Any] = cmdHandler.calculatorCommandHandler
      override val infraApi: InfraApi.Service                                      = api.infraApi
    }

  def buildResultHandler(loggerEnv: Logger, formatterEnv: ResultFormatter): ZManaged[Any, Nothing, ResultHandler] =
    ZManaged
      .fromEffect(ResultHandler.createResultHandler)
      .provide(new Logger with ResultFormatter {
        override val logger: Logger.Service[Any]        = loggerEnv.logger
        override val formatter: ResultFormatter.Service = formatterEnv.formatter
      })

  def buildCalculatorCmdHandler(calculatorEnv: Calculator, publishResult: ResultPublisher): ZManaged[Any, Nothing, CalculatorCommandHandler] =
    ZManaged
      .fromEffect(CalculatorCommandHandler.createCalculatorCommandHandler)
      .provide(new ResultPublisher with Calculator {
        override val resultPublisher: ResultPublisher.Service[Any] = publishResult.resultPublisher
        override val calculator: Calculator.Service[Any]           = calculatorEnv.calculator
      })

  def buildInfraApi(loggerEnv: Logger, blockingEnv: Blocking, clockEnv: Clock, clientP: ClientProvider): ZManaged[Any, Nothing, InfraApi] =
    ZManaged
      .fromEffect(InfraApi.makeLiveInfraApi)
      .provide(new Logger with Clock with Blocking with ClientProvider {
        override val logger: Logger.Service[Any]                       = loggerEnv.logger
        override val clock: Clock.Service[Any]                         = clockEnv.clock
        override val adminClientProvider: ClientProvider.Provider[Any] = clientP.adminClientProvider
        override val blocking: Blocking.Service[Any]                   = blockingEnv.blocking
      })

  private def buildClientProvider(cf: ConnectionFactory, loggerEnv: Logger, blockingEnv: Blocking) =
    ClientProvider
      .createLiveAdminClientProvider(cf)
      .provide(new Logger with Blocking {
        override val logger: Logger.Service[Any]     = loggerEnv.logger
        override val blocking: Blocking.Service[Any] = blockingEnv.blocking
      })

}
