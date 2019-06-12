package freskog.effects

import java.io.IOException

import com.rabbitmq.client.ShutdownSignalException
import scalaz.zio.{ console, ZIO }

import scala.concurrent.TimeoutException

package object rabbitmq {

  val convertToIOException: PartialFunction[Throwable, IOException] = {
    case io: IOException              => io
    case sig: ShutdownSignalException => new IOException(sig)
    case t: TimeoutException          => new IOException(t)
  }

  def printErrMsg(t: Throwable): ZIO[Any, Nothing, Unit] =
    console.putStrLn(s"error occurred in finalizer: $t").provide(console.Console.Live)

}
