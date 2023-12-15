package sttp.tapir.perf

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{PublicEndpoint, endpoint, path, stringBody}

import scala.io.StdIn
import sttp.tapir.server.ServerEndpoint

object Common {
  def genTapirEndpoint(n: Int): PublicEndpoint[Int, String, String, Any] = endpoint.get
    .in("path" + n.toString)
    .in(path[Int]("id"))
    .errorOut(stringBody)
    .out(stringBody)

  def genPostStrTapirEndpoint(n: Int): PublicEndpoint[String, String, String, Any] = endpoint.post
    .in("path" + n.toString)
    .in(stringBody)
    .errorOut(stringBody)
    .out(stringBody)

  def replyWithStr[F[_]](endpoint: PublicEndpoint[_, _, String, Any])(implicit monad: MonadError[F]): ServerEndpoint[Any, F] =
    endpoint.serverLogicSuccess[F](_ => monad.eval("ok"))


  def blockServer(): Unit = {
    println(Console.BLUE + "Server now online. Please navigate to http://localhost:8080/path0/1\nPress RETURN to stop..." + Console.RESET)
    StdIn.readLine()
    println("Server terminated")
  }
}
