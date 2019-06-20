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

  val topology: ZIO[Any, IOException, Declaration] =
    ZIO.fromEither(topologyDeclaration).mapError(new IOException(_))

  val resultPublisher: ZIO[Publisher, IOException, List[ResultEvent] => ZIO[Any, IOException, Unit]] =
    for {
      topology  <- topology
      publishFn <- publishConfirmsTo(resultsExchange, topology)
    } yield (events: List[ResultEvent]) => ZIO.foreach_(events.map(_.toString))(publishFn)

  val commandConsumer: ZIO[CalculatorInput with Consumer with Publisher, IOException, Unit] =
    for {
      topology       <- topology
      publishFn      <- resultPublisher
      commandHandler <- handleCalculatorInput(0)
      _              <- callbackConsumerOn(commandQueue, topology)(commandHandler(_) >>= publishFn)
    } yield ()

  val resultConsumer: ZIO[ResultPrinter with Consumer, IOException, Unit] =
    topology >>= (pollingConsumerOn(resultsQueue, _)(print))

}
