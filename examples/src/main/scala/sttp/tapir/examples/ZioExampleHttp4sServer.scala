// {cat=Hello, World!; effects=ZIO; server=http4s; json=circe; docs=Swagger UI}: Exposing an endpoint using the http4s server

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.23
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.23
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server-zio:1.11.23
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.23
//> using dep com.softwaremill.sttp.tapir::tapir-zio:1.11.23
//> using dep org.http4s::http4s-ember-server:0.23.30
//> using dep dev.zio::zio-interop-cats:23.1.0.3

package sttp.tapir.examples

import cats.syntax.all.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*
import zio.interop.catz.*
import zio.{ExitCode, Task, URIO, ZIO, ZIOAppDefault}

object ZioExampleHttp4sServer extends ZIOAppDefault:
  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[Task] = ZHttp4sServerInterpreter()
    .from(petEndpoint.zServerLogic { petId =>
      if (petId == 35) {
        ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
      } else {
        ZIO.fail("Unknown pet id")
      }
    })
    .toRoutes

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[Any, Any] = petEndpoint.zServerLogic { petId =>
    if (petId == 35) {
      ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      ZIO.fail("Unknown pet id")
    }
  }
  val petServerRoutes: HttpRoutes[Task] = ZHttp4sServerInterpreter().from(petServerEndpoint).toRoutes

  //

  val swaggerRoutes: HttpRoutes[Task] =
    ZHttp4sServerInterpreter()
      .from(SwaggerInterpreter().fromEndpoints[Task](List(petEndpoint), "Our pets", "1.0"))
      .toRoutes

  // Starting the server
  val serve: Task[Unit] = EmberServerBuilder
    .default[Task]
    .withHttpApp(Router("/" -> (petRoutes <+> swaggerRoutes)).orNotFound)
    .build
    .useForever

  override def run: URIO[Any, ExitCode] = serve.exitCode
