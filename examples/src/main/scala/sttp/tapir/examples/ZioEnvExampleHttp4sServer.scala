package sttp.tapir.examples

import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import zio.interop.catz._
import zio._

object ZioEnvExampleHttp4sServer extends ZIOAppDefault {
  // Domain classes, services, layers
  case class Pet(species: String, url: String)

  object PetLayer {
    trait PetService {
      def find(id: Int): ZIO[Any, String, Pet]
    }

    object PetService {
      val live: ZLayer[Console, String, PetService] =
        ZIO
          .serviceWith[Console] { console =>
            new PetService {
              def find(petId: Int): ZIO[Any, String, Pet] =
                console.printLine(s"Got request for pet: $petId").mapError(_.getMessage) *> {
                  if (petId == 35) {
                    UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
                  } else {
                    IO.fail("Unknown pet id")
                  }
                }
            }
          }
          .toLayer

      def find(id: Int): ZIO[PetService, String, Pet] = ZIO.serviceWithZIO(_.find(id))
    }
  }
  import PetLayer.PetService

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[RIO[PetService with Clock, *]] =
    ZHttp4sServerInterpreter().from(petEndpoint.zServerLogic(petId => PetService.find(petId))).toRoutes

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[PetService, Any] =
    petEndpoint.zServerLogic(petId => PetService.find(petId))
  val petServerRoutes: HttpRoutes[RIO[PetService with Clock, *]] =
    ZHttp4sServerInterpreter().from(List(petServerEndpoint)).toRoutes

  // Documentation
  val swaggerRoutes: HttpRoutes[RIO[PetService with Clock, *]] =
    ZHttp4sServerInterpreter()
      .from(SwaggerInterpreter().fromEndpoints[RIO[PetService with Clock, *]](List(petEndpoint), "Our pets", "1.0"))
      .toRoutes

  // Starting the server
  val serve: ZIO[ZEnv with PetService, Throwable, Unit] = ZIO.runtime[ZEnv with PetService].flatMap { implicit runtime =>
    BlazeServerBuilder[RIO[PetService with Clock, *]]
      .withExecutionContext(runtime.runtimeConfig.executor.asExecutionContext)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> (petRoutes <+> swaggerRoutes)).orNotFound)
      .serve
      .compile
      .drain
  }

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] =
    serve.provideCustomLayer(PetService.live).exitCode
}
