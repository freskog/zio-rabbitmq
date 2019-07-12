package freskog.effects.infra.rabbitmq

import java.io.IOException

import freskog.effects.infra.rabbitmq.topology.Declaration
import zio.ZIO

object TopologyDeclaration {
  val commandQueue    = "commandQueue"
  val commandExchange = "commandExchange"
  val resultExchange  = "resultExchange"
  val resultQueue     = "resultQueue"

  val topologyDeclaration: Either[String, Declaration] =
    Right(Declaration.empty) flatMap
      (_.addQueue(commandQueue)) flatMap
      (_.addQueue(resultQueue)) flatMap
      (_.addExchange(commandExchange)) flatMap
      (_.addExchange(resultExchange)) flatMap
      (_.bind(commandQueue, commandExchange)) flatMap
      (_.bind(resultQueue, resultExchange))

  val topology: ZIO[Any, IOException, Declaration] =
    ZIO.fromEither(topologyDeclaration).mapError(new IOException(_))

}
