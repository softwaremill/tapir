package sttp.tapir.examples

import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import sttp.tapir.server.http4s.ztapir._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import sttp.tapir.ztapir._
import zio.clock.Clock
import zio.interop.catz._
import zio.{App, ExitCode, IO, RIO, UIO, URIO, ZEnv, ZIO}

object ZioExampleHttp4sServer extends App {
  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: ZEndpoint[Int, String, Pet] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[RIO[Clock, *]] = petEndpoint.toRoutes { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[Any, Int, String, Pet] = petEndpoint.zServerLogic { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }
  val petServerRoutes: HttpRoutes[RIO[Clock, *]] = petServerEndpoint.toRoutes

  //

  val yaml: String = {
    import sttp.tapir.docs.openapi._
    import sttp.tapir.openapi.circe.yaml._
    List(petEndpoint).toOpenAPI("Our pets", "1.0").toYaml
  }

  // Starting the server
  val serve: ZIO[ZEnv, Throwable, Unit] =
    ZIO.runtime[ZEnv].flatMap { implicit runtime => // This is needed to derive cats-effect instances for that are needed by http4s
      BlazeServerBuilder[RIO[Clock, *]](runtime.platform.executor.asEC)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> (petRoutes <+> new SwaggerHttp4s(yaml).routes)).orNotFound)
        .serve
        .compile
        .drain
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = serve.exitCode
}
