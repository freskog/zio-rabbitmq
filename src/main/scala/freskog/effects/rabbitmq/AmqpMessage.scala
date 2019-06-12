package freskog.effects.rabbitmq

import com.rabbitmq.client.Channel

case class AmqpMessage(body:String, tag:Long, fromChannel:Channel)