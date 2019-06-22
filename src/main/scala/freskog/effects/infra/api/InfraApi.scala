package freskog.effects.infra.api

import java.io.IOException

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.app.dto.{ CalculatorCommand, ResultEvent }
import freskog.effects.infra.logger._
import freskog.effects.infra.rabbitmq.TopologyDeclaration
import freskog.effects.infra.rabbitmq.consumer._
import freskog.effects.infra.rabbitmq.publisher._
import scalaz.zio.ZIO

trait InfraApi extends Serializable {
  val infraApi: InfraApi.Service[Any]
}

object InfraApi extends Serializable {

  trait Service[R] extends Serializable {
    def handleCalculatorCommand[R1, E](cmd: CalculatorCommand => ZIO[R1, E, Unit]): ZIO[R with R1, E, Unit]

    def handleResultEvent[R1, E](ev: ResultEvent => ZIO[R1, E, Unit]): ZIO[R with R1, E, Unit]

    def publishResultEvent(ev: ResultEvent): ZIO[R, Nothing, Unit]
  }

  def makeLiveInfraApi(cf: ConnectionFactory): ZIO[Any, Nothing, InfraApi] =
    for {
      declaredTopology <- TopologyDeclaration.topology.orDie
      commandConsumer  <- Consumer.makeCallbackConsumer(cf, TopologyDeclaration.commandQueue, declaredTopology)
      resultConsumer   <- Consumer.makePollingConsumer(cf, TopologyDeclaration.resultQueue, declaredTopology)
      resultPublisher  <- Publisher.makePublisherWithConfirms(cf, TopologyDeclaration.resultExchange, declaredTopology)
      loggerEnv        <- Logger.makeLogger("InfraAPI")
    } yield new InfraApi {
      override val infraApi: Service[Any] =
        new Service[Any] {
          override def handleCalculatorCommand[R1, E](handleCmd: CalculatorCommand => ZIO[R1, E, Unit]): ZIO[R1, E, Unit] =
            commandConsumer.consumer.consumeUsing(CalculatorCommand.fromString(_).fold(warn(_).provide(loggerEnv), handleCmd))

          override def handleResultEvent[R1, E](handleEv: ResultEvent => ZIO[R1, E, Unit]): ZIO[R1, E, Unit] =
            resultConsumer.consumer.consumeUsing(ResultEvent.fromString(_).fold(warn(_).provide(loggerEnv), handleEv))

          override def publishResultEvent(ev: ResultEvent): ZIO[Any, Nothing, Unit] =
            resultPublisher.publisher.publishMessage(ev.toString).catchAll(throwable(_).provide(loggerEnv))
        }
    }
}
