package sttp.tapir.examples

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zhttp.http.HttpApp
import zhttp.service.Server
import zio._
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

object HelloWorldZioHttpServer extends App {
  // a simple string-only endpoint
  val helloWorld: Endpoint[String, Unit, String, Any] =
    endpoint.get
      .in("hello")
      .in(path[String]("name"))
      .out(stringBody)

  // an endpoint which responds which json, using zio-json
  case class AddResult(x: Int, y: Int, result: Int)
  object AddResult {
    implicit val decoder: JsonDecoder[AddResult] = DeriveJsonDecoder.gen[AddResult]
    implicit val encoder: JsonEncoder[AddResult] = DeriveJsonEncoder.gen[AddResult]
  }
  val add: Endpoint[(Int, Int), Unit, AddResult, Any] =
    endpoint.get
      .in("add")
      .in(path[Int]("x"))
      .in(path[Int]("y"))
      .out(jsonBody[AddResult])

  // converting the endpoint descriptions to the Http type
  val app: HttpApp[Any, Throwable] =
    ZioHttpInterpreter().toHttp(helloWorld)(name => ZIO.succeed(Right(s"Hello, $name!"))) <>
      ZioHttpInterpreter().toHttp(add) { case (x, y) => ZIO.succeed(Right(AddResult(x, y, x + y))) }

  // starting the server
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
