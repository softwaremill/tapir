package sttp.tapir.examples

import sttp.tapir._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.HttpApp
import zhttp.service.Server
import zio._
import zio.blocking.Blocking

object HelloWorldZioHttpServer extends App {

  val helloWorld: Endpoint[String, Throwable, String, Any] =
    endpoint
      .get
      .in("hello")
      .in(path[String]("name"))
      .errorOut(plainBody[String].map(err => new Throwable(err))(_.getMessage))
      .out(stringBody)


  val add: Endpoint[(Int, Int), Throwable, String, Any] =
    endpoint
      .get
      .in("add")
      .in(path[Int]("x"))
      .in(path[Int]("y"))
      .errorOut(plainBody[String].map(err => new Throwable(err))(_.getMessage))
      .out(stringBody)

  val app: HttpApp[Blocking, Throwable] = ZioHttpInterpreter().toRoutes(helloWorld)(name => ZIO.succeed(s"Hello $name")) <>
    ZioHttpInterpreter().toRoutes(add) { case (x, y) => ZIO.succeed(s"Adding up ${x + y}") }

  // starting the server
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
