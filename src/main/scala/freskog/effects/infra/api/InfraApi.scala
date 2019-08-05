package freskog.effects.infra.api

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.app.dto.{ CalculatorCommand, ResultEvent }
import freskog.effects.app.logger.{ Logger, _ }
import freskog.effects.infra.rabbitmq.TopologyDeclaration
import freskog.effects.infra.rabbitmq.admin.ClientProvider
import freskog.effects.infra.rabbitmq.consumer._
import freskog.effects.infra.rabbitmq.publisher._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{ UIO, ZIO }

trait InfraApi extends Serializable {
  val infraApi: InfraApi.Service
}

object InfraApi extends Serializable {

  trait Service extends Serializable {
    def handleCalculatorCommand(cmd: CalculatorCommand => UIO[Unit]): UIO[Unit]

    def handleResultEvent(ev: ResultEvent => UIO[Unit]): UIO[Unit]

    def publishResultEvent(ev: ResultEvent): UIO[Unit]
  }

  val makeLiveInfraApi: ZIO[Logger with Clock with Blocking with ClientProvider, Nothing, InfraApi] =
    for {
      declaredTopology <- TopologyDeclaration.topology.orDie
      commandConsumer  <- Consumer.createConsumer(declaredTopology, TopologyDeclaration.commandQueue)
      resultConsumer   <- Consumer.createConsumer(declaredTopology, TopologyDeclaration.resultQueue)
      resultPublisher  <- Publisher.createPublisher(declaredTopology, TopologyDeclaration.resultExchange)
      loggerEnv        <- ZIO.environment[Logger]
    } yield new InfraApi {
      override val infraApi: Service =
        new Service {
          override def handleCalculatorCommand(handleCmd: CalculatorCommand => UIO[Unit]): UIO[Unit] =
            commandConsumer.consumer.consume(CalculatorCommand.fromString(_).fold(warn(_).provide(loggerEnv), handleCmd))

          override def handleResultEvent(handleEv: ResultEvent => UIO[Unit]): UIO[Unit] =
            resultConsumer.consumer.consume(ResultEvent.fromString(_).fold(warn(_).provide(loggerEnv), handleEv))

          override def publishResultEvent(ev: ResultEvent): ZIO[Any, Nothing, Unit] =
            resultPublisher.publisher.publishMessage(ev.toString).catchAll(throwable(_).provide(loggerEnv))
        }
    }
}
