package sttp.tapir.examples

import sttp.tapir._
import sttp.tapir.server.zhttp.ZHttpInterpreter
import zhttp.http.{Http, HttpApp, Request, Response}
import zhttp.service.Server
import zio._
import zio.blocking.Blocking

object HelloWorldZHttpServer extends App {

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

  private val value: Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = ZHttpInterpreter.toHttp(helloWorld)(name => ZIO.succeed(s"Hello $name"))
  private val value1: Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = ZHttpInterpreter.toHttp(add) { case (x, y) => ZIO.succeed(s"Adding up ${x + y}") }
  val app: HttpApp[Blocking, Throwable] =
    value <> value1



  // starting the server
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
