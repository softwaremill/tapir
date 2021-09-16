package sttp.tapir.examples

import io.circe.generic.auto._
import sttp.tapir.Endpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.SwaggerUI
import sttp.tapir.ztapir._
import zhttp.http.HttpApp
import zhttp.service.Server
import zio.{App, ExitCode, IO, Task, UIO, URIO, ZIO}

object ZioExampleZioHttpServer extends App {
  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: Endpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpApp[Any, Throwable] =
    ZioHttpInterpreter().toHttp(petEndpoint)(petId =>
      if (petId == 35) ZIO.succeed(Right(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir")))
      else ZIO.succeed(Left("Unknown pet id"))
    )

  // Same as above, but combining endpoint description with server logic:
  // TODO: convert to zServerLogic once zio-http integration supports WebSockets
  val petServerEndpoint: ServerEndpoint[Int, String, Pet, Any, Task] = petEndpoint.serverLogic { petId =>
    (if (petId == 35) {
       UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
     } else {
       IO.fail("Unknown pet id")
     }).either.resurrect
  }
  val petServerRoutes: HttpApp[Any, Throwable] = ZioHttpInterpreter().toHttp(List(petServerEndpoint))

  //

  val yaml: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._
    OpenAPIDocsInterpreter().toOpenAPI(petEndpoint, "Our pets", "1.0").toYaml
  }

  val swaggerRoutes: HttpApp[Any, Throwable] = ZioHttpInterpreter().toHttp(SwaggerUI[Task](yaml))

  // Starting the server
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8080, petRoutes <> swaggerRoutes).exitCode
}
