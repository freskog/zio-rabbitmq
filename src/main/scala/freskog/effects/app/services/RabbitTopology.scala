package freskog.effects.app.services

import java.io.IOException

import freskog.effects.calculator.ResultEvent
import freskog.effects.rabbitmq.consumer._
import freskog.effects.rabbitmq.publisher._
import freskog.effects.rabbitmq.topology._
import scalaz.zio._

object RabbitTopology {

  val commandQueue    = "commandQueue"
  val commandExchange = "commandEx"
  val resultsExchange = "resultExchange"
  val resultsQueue    = "resultQueue"

  val topologyDeclaration: Either[String, Declaration] =
    Right(Declaration.empty) flatMap
      (_.addQueue(commandQueue)) flatMap
      (_.addQueue(resultsQueue)) flatMap
      (_.addExchange(commandExchange)) flatMap
      (_.addExchange(resultsExchange)) flatMap
      (_.bind(commandQueue, commandExchange)) flatMap
      (_.bind(resultsQueue, resultsExchange))

  val topology: ZIO[TopologyClient, IOException, Unit] =
    ZIO.fromEither(topologyDeclaration).mapError(new IOException(_)) >>= createTopology

  val resultPublisher: ZIO[Publisher, Nothing, List[ResultEvent] => ZIO[Any, IOException, Unit]] =
    for( publishFn <- publishConfirmsTo(resultsExchange, topologyDeclaration.getOrElse(Declaration.empty)))
      yield (events:List[ResultEvent]) => ZIO.foreach_(events.map(_.toString))(publishFn)

  val commandConsumer: ZIO[CalculatorInput with Publisher with Consumer, IOException, Unit] =
    for {
      publishFn      <- resultPublisher
      commandHandler <- handleCalculatorInput(0)
      _              <- callbackConsumerOn(commandQueue,topologyDeclaration.getOrElse(Declaration.empty))(commandHandler(_) >>= publishFn)
    } yield ()

  val resultConsumer: ZIO[ResultPrinter with Consumer, Nothing, Unit] =
    pollingConsumerOn(resultsQueue, topologyDeclaration.getOrElse(Declaration.empty))(print)

}
