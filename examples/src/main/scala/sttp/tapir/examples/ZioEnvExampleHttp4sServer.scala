package sttp.tapir.examples

import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import sttp.tapir.ztapir._
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
        console.get.putStrLn(s"Got request for pet: $petId") *> {
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
  val petEndpoint: ZEndpoint[Int, String, Pet] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[RIO[PetService with Clock, *]] = petEndpoint.toRoutes(petId => PetService.find(petId))

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[PetService, Int, String, Pet] = petEndpoint.zServerLogic(petId => PetService.find(petId))
  val petServerRoutes: HttpRoutes[RIO[PetService with Clock, *]] = petServerEndpoint.toRoutes

  // Documentation
  val yaml: String = {
    import sttp.tapir.docs.openapi._
    import sttp.tapir.openapi.circe.yaml._
    List(petEndpoint).toOpenAPI("Our pets", "1.0").toYaml
  }

  // Starting the server
  val serve: ZIO[ZEnv with PetService, Throwable, Unit] = ZIO.runtime[ZEnv with PetService].flatMap { implicit runtime =>
    BlazeServerBuilder[RIO[PetService with Clock, *]](runtime.platform.executor.asEC)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> (petRoutes <+> new SwaggerHttp4s(yaml).routes)).orNotFound)
      .serve
      .compile
      .drain
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    serve.provideCustomLayer(PetService.live).exitCode
  }
}
