package freskog.effects.infra.rabbitmq.topology

case class Declaration(queues: List[AmqpQueue], exchanges: List[FanoutExchange], bindings: Map[AmqpQueue, FanoutExchange]) {
  def addQueue(name: String): Either[String, Declaration] =
    if (queues.map(_.name).contains(name)) Left(s"Bad Queue: $name already defined")
    else Right(copy(queues = AmqpQueue(name) :: queues))

  def addExchange(name: String): Either[String, Declaration] =
    if (exchanges.map(_.name).contains(name)) Left(s"Bad Exchange: $name already defined")
    else Right(copy(exchanges = FanoutExchange(name) :: exchanges))

  def bind(queue: String, exchange: String): Either[String, Declaration] =
    if (!queues.map(_.name).contains(queue) || !exchanges.map(_.name).contains(exchange))
      Left(s"Bad Binding: $queue and/or $exchange not declared")
    else
      Right(copy(bindings = bindings.updated(AmqpQueue(queue), FanoutExchange(exchange))))
}

object Declaration {
  val empty = Declaration(Nil, Nil, Map.empty)
}
