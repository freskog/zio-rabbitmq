name := "zio-imperative-effects"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-zio" % "1.0-RC5",
  "org.scalaz" %% "scalaz-zio-streams" % "1.0-RC5",
  "org.scalaz" %% "scalaz-zio-testkit" % "1.0-RC5" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "com.rabbitmq" % "amqp-client" % "5.7.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)