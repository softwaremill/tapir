package sttp.tapir.examples

import io.circe.generic.auto._
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import zio.http.HttpApp
import zio.http.{Server, ServerConfig}
import zio.{ExitCode, Task, URIO, ZIO, ZIOAppDefault}

object ZioExampleZioHttpServer extends ZIOAppDefault {
  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpApp[Any, Throwable] =
    ZioHttpInterpreter().toHttp(
      petEndpoint.zServerLogic(petId =>
        if (petId == 35) ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
        else ZIO.fail("Unknown pet id")
      )
    )

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[Any, Any] = petEndpoint.zServerLogic { petId =>
    if (petId == 35) {
      ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      ZIO.fail("Unknown pet id")
    }
  }

  // Docs
  val swaggerEndpoints: List[ZServerEndpoint[Any, Any]] = SwaggerInterpreter().fromEndpoints[Task](List(petEndpoint), "Our pets", "1.0")

  // Starting the server
  val routes: HttpApp[Any, Throwable] = ZioHttpInterpreter().toHttp(List(petServerEndpoint) ++ swaggerEndpoints)

  override def run: URIO[Any, ExitCode] =
    Server
      .serve(routes.withDefaultErrorResponse)
      .provide(
        ServerConfig.live(ServerConfig.default.port(8080)),
        Server.live
      )
      .exitCode
}
