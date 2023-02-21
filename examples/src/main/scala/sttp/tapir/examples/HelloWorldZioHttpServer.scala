package sttp.tapir.examples

import sttp.tapir.PublicEndpoint
import sttp.tapir.ztapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.HttpApp
import zio.http.{Server, ServerConfig}
import zio._
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

object HelloWorldZioHttpServer extends ZIOAppDefault {
  // a simple string-only endpoint
  val helloWorld: PublicEndpoint[String, Unit, String, Any] =
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
  val add: PublicEndpoint[(Int, Int), Unit, AddResult, Any] =
    endpoint.get
      .in("add")
      .in(path[Int]("x"))
      .in(path[Int]("y"))
      .out(jsonBody[AddResult])

  // converting the endpoint descriptions to the Http type
  val app: HttpApp[Any, Throwable] =
    ZioHttpInterpreter().toHttp(helloWorld.zServerLogic(name => ZIO.succeed(s"Hello, $name!"))) ++
      ZioHttpInterpreter().toHttp(add.zServerLogic { case (x, y) => ZIO.succeed(AddResult(x, y, x + y)) })

  // starting the server
  override def run =
    Server
      .serve(app.withDefaultErrorResponse)
      .provide(
        ServerConfig.live(ServerConfig.default.port(8090)),
        Server.live
      )
      .exitCode
}
