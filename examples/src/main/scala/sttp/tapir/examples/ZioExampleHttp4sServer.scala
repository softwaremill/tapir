package sttp.tapir.examples

import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.tapir.PublicEndpoint
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import zio.interop.catz._
import zio._

object ZioExampleHttp4sServer extends ZIOAppDefault {
  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[RIO[Clock, *]] = ZHttp4sServerInterpreter()
    .from(petEndpoint.zServerLogic { petId =>
      if (petId == 35) {
        UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
      } else {
        IO.fail("Unknown pet id")
      }
    })
    .toRoutes

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[Any, Any] = petEndpoint.zServerLogic { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }
  val petServerRoutes: HttpRoutes[RIO[Clock, *]] = ZHttp4sServerInterpreter().from(petServerEndpoint).toRoutes

  //

  val swaggerRoutes: HttpRoutes[RIO[Clock, *]] =
    ZHttp4sServerInterpreter()
      .from(SwaggerInterpreter().fromEndpoints[RIO[Clock, *]](List(petEndpoint), "Our pets", "1.0"))
      .toRoutes

  // Starting the server
  val serve: ZIO[ZEnv, Throwable, Unit] =
    ZIO.runtime[ZEnv].flatMap { implicit runtime => // This is needed to derive cats-effect instances for that are needed by http4s
      BlazeServerBuilder[RIO[Clock, *]]
        .withExecutionContext(runtime.runtimeConfig.executor.asExecutionContext)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> (petRoutes <+> swaggerRoutes)).orNotFound)
        .serve
        .compile
        .drain
    }

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = serve.exitCode
}
