package sttp.tapir.examples

import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.SwaggerUI
import sttp.tapir.ztapir._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._
import zio.{App, ExitCode, Has, IO, RIO, UIO, URIO, ZEnv, ZIO, ZLayer}

object ZioEnvExampleHttp4sServer extends App {
  // Domain classes, services, layers
  case class Pet(species: String, url: String)

  object PetLayer {
    type PetService = Has[PetService.Service]

    object PetService {
      trait Service {
        def find(id: Int): ZIO[Any, String, Pet]
      }

      val live: ZLayer[Console, String, PetService] = ZLayer.fromFunction { console: Console => petId: Int =>
        console.get.putStrLn(s"Got request for pet: $petId").mapError(_.getMessage) *> {
          if (petId == 35) {
            UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
          } else {
            IO.fail("Unknown pet id")
          }
        }
      }

      def find(id: Int): ZIO[PetService, String, Pet] = ZIO.accessM(_.get.find(id))
    }
  }
  import PetLayer.PetService

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[RIO[PetService with Clock with Blocking, *]] =
    ZHttp4sServerInterpreter().from(petEndpoint.zServerLogic(petId => PetService.find(petId))).toRoutes

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[PetService, Any] =
    petEndpoint.zServerLogic(petId => PetService.find(petId))
  val petServerRoutes: HttpRoutes[RIO[PetService with Clock with Blocking, *]] =
    ZHttp4sServerInterpreter().from(List(petServerEndpoint)).toRoutes

  // Documentation
  val yaml: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._
    OpenAPIDocsInterpreter().toOpenAPI(List(petEndpoint), "Our pets", "1.0").toYaml
  }

  val swaggerRoutes: HttpRoutes[RIO[PetService with Clock with Blocking, *]] =
    ZHttp4sServerInterpreter().from(SwaggerUI[RIO[PetService with Clock with Blocking, *]](yaml)).toRoutes

  // Starting the server
  val serve: ZIO[ZEnv with PetService, Throwable, Unit] = ZIO.runtime[ZEnv with PetService].flatMap { implicit runtime =>
    BlazeServerBuilder[RIO[PetService with Clock with Blocking, *]](runtime.platform.executor.asEC)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> (petRoutes <+> swaggerRoutes)).orNotFound)
      .serve
      .compile
      .drain
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    serve.provideCustomLayer(PetService.live).exitCode
  }
}
