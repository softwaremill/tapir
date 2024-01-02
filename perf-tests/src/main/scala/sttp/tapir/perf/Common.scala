package sttp.tapir.perf

import sttp.monad.MonadError

import scala.io.StdIn
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.PublicEndpoint

object Common {
  def replyWithStr[F[_]](endpoint: PublicEndpoint[_, _, String, Any])(implicit monad: MonadError[F]): ServerEndpoint[Any, F] =
    endpoint.serverLogicSuccess[F](_ => monad.eval("ok"))


  def blockServer(): Unit = {
    println(Console.BLUE + "Server now online. Please navigate to http://localhost:8080/path0/1\nPress RETURN to stop..." + Console.RESET)
    StdIn.readLine()
    println("Server terminated")
  }
}
