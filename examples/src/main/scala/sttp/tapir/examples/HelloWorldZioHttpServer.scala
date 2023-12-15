package sttp.tapir.examples

import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.http.{HttpApp, Server}
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
  val app: HttpApp[Any] =
    ZioHttpInterpreter().toHttp(helloWorld.zServerLogic(name => ZIO.succeed(s"Hello, $name!"))) ++
      ZioHttpInterpreter().toHttp(add.zServerLogic { case (x, y) => ZIO.succeed(AddResult(x, y, x + y)) })

  // starting the server
  override def run =
    Server
      .serve(app)
      .provide(
        ZLayer.succeed(Server.Config.default.port(8080)),
        Server.live
      )
      .exitCode
}
