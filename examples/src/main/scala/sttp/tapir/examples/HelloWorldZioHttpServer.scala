package sttp.tapir.examples

import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.HttpApp
import zhttp.service.Server
import zio._

object HelloWorldZioHttpServer extends App {
  val helloWorld: Endpoint[String, Unit, String, Any] =
    endpoint.get
      .in("hello")
      .in(path[String]("name"))
      .out(stringBody)

  val add: Endpoint[(Int, Int), Unit, String, Any] =
    endpoint.get
      .in("add")
      .in(path[Int]("x"))
      .in(path[Int]("y"))
      .out(stringBody)

  val app: HttpApp[Any, Throwable] =
    ZioHttpInterpreter().toHttp(helloWorld)(name => ZIO.succeed(Right(s"Hello, $name!"))) <>
      ZioHttpInterpreter().toHttp(add) { case (x, y) => ZIO.succeed(Right(s"$x + $y equals: ${x + y}")) }

  // starting the server
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
