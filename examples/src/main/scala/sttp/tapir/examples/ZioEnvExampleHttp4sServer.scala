package sttp.tapir.examples

import cats.syntax.all.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*
import zio.interop.catz.*
import zio.{Console, IO, Layer, RIO, ZIO, ZIOAppDefault, ZLayer}

object ZioEnvExampleHttp4sServer extends ZIOAppDefault {
  // Domain classes, services, layers
  case class Pet(species: String, url: String)

  trait PetService {
    def find(id: Int): ZIO[Any, String, Pet]
  }

  object PetService {
    def find(id: Int): ZIO[PetService, String, Pet] = ZIO.environmentWithZIO(_.get.find(id))

    val live: Layer[String, PetService] = ZLayer.succeed {
      new PetService {
        override def find(petId: Int): IO[String, Pet] = {
          Console.printLine(s"Got request for pet: $petId").mapError(_.getMessage) zipRight {
            if (petId == 35) {
              ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
            } else {
              ZIO.fail("Unknown pet id")
            }
          }
        }
      }
    }
  }

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[RIO[PetService, *]] =
    ZHttp4sServerInterpreter().from(petEndpoint.zServerLogic(petId => PetService.find(petId))).toRoutes

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[PetService, Any] =
    petEndpoint.zServerLogic(petId => PetService.find(petId))
  val petServerRoutes: HttpRoutes[RIO[PetService, *]] =
    ZHttp4sServerInterpreter().from(List(petServerEndpoint)).toRoutes

  // Documentation
  val swaggerRoutes: HttpRoutes[RIO[PetService, *]] =
    ZHttp4sServerInterpreter()
      .from(SwaggerInterpreter().fromEndpoints[RIO[PetService, *]](List(petEndpoint), "Our pets", "1.0"))
      .toRoutes

  // Starting the server
  val serve: ZIO[PetService, Throwable, Unit] = {
    ZIO.executor.flatMap(executor =>
      BlazeServerBuilder[RIO[PetService, *]]
        .withExecutionContext(executor.asExecutionContext)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> (petRoutes <+> swaggerRoutes)).orNotFound)
        .serve
        .compile
        .drain
    )

  }

  override def run =
    serve.provide(PetService.live).exitCode
}
