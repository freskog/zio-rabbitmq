package freskog.effects.app

import java.io.IOException

import freskog.effects.app.services.{ CalculatorInput, RabbitTopology, ResultPrinter }
import freskog.effects.calculator.Calculator
import freskog.effects.rabbitmq.Schedules
import freskog.effects.rabbitmq.admin.AdminClient
import freskog.effects.rabbitmq.consumer.Consumer
import freskog.effects.rabbitmq.publisher.Publisher
import freskog.effects.rabbitmq.topology.TopologyClient
import scalaz.zio._

object DemoApp extends App {

  val program: ZIO[ResultPrinter with Consumer with CalculatorInput with Publisher with TopologyClient, IOException, Unit] =
    for {
      f1 <- RabbitTopology.commandConsumer.fork
      f2 <- RabbitTopology.resultConsumer.fork
      _  <- f1.join zip f2.join
    } yield ()

  object Live
      extends ResultPrinter.Live
      with Consumer.Live
      with CalculatorInput.Live
      with Calculator.Live
      with Publisher.Live
      with TopologyClient.Live
      with AdminClient.Live {}

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program
      .provide(Live)
      .sandbox
      .retry(Schedules.restartFiber("initializing program"))
      .option *> UIO.succeed(0)

}
