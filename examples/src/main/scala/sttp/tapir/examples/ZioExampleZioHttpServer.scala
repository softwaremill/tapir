// {cat=Hello, World!; effects=ZIO; server=zio-http; json=circe; docs=Swagger UI}: Exposing an endpoint using the ZIO HTTP server

//> using option -Ykind-projector
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio:1.11.8

package sttp.tapir.examples

import io.circe.generic.auto.*
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*
import zio.http.{Response => ZioHttpResponse, Routes, Server}
import zio.{ExitCode, Task, URIO, ZIO, ZIOAppDefault, ZLayer}

object ZioExampleZioHttpServer extends ZIOAppDefault:
  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: Routes[Any, ZioHttpResponse] =
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
  val routes: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(List(petServerEndpoint) ++ swaggerEndpoints)

  override def run: URIO[Any, ExitCode] =
    Server
      .serve(routes)
      .provide(
        ZLayer.succeed(Server.Config.default.port(8080)),
        Server.live
      )
      .exitCode
