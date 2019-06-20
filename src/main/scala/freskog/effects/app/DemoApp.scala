package freskog.effects.app

import java.io.IOException

import com.rabbitmq.client.ConnectionFactory
import freskog.effects.app.services.{CalculatorInput, RabbitTopology, ResultPrinter}
import freskog.effects.calculator.Calculator
import freskog.effects.rabbitmq.Schedules
import freskog.effects.rabbitmq.consumer.Consumer
import freskog.effects.rabbitmq.publisher.Publisher
import scalaz.zio._

object DemoApp extends App {

  type FullEnv = ResultPrinter with Consumer with CalculatorInput with Publisher

  val program: ZIO[FullEnv, IOException, Unit] =
    for {
      f1 <- RabbitTopology.commandConsumer.fork
      f2 <- RabbitTopology.resultConsumer.fork
      _  <- f1.join zip f2.join
    } yield ()

  def liveEnv(cf: ConnectionFactory): CalculatorInput with Consumer with Publisher with ResultPrinter =
    new CalculatorInput.Live with Calculator.Live with Consumer.Live with Publisher.Live with ResultPrinter.Live {
      override val connectionFactory: ConnectionFactory = cf
    }

  val cf: ConnectionFactory = {
    val cf = new ConnectionFactory
    cf.setAutomaticRecoveryEnabled(false)
    cf.setTopologyRecoveryEnabled(false)
    cf
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program
      .provide(liveEnv(cf))
      .sandbox.retry(Schedules.restartFiber("initializing program")).option *> UIO.succeed(0)

}
