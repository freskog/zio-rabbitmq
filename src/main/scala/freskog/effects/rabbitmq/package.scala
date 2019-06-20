package freskog.effects

import scalaz.zio.{ZIO, console}

package object rabbitmq {

  def printErrMsg(t: Throwable): ZIO[Any, Nothing, Unit] =
    console.putStrLn(s"error occurred in finalizer: $t").provide(console.Console.Live)

}
