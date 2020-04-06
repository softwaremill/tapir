package sttp.tapir.examples

import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{IO, Runtime, Task, UIO}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import cats.implicits._
import LayerEndpoint.UserService

object ZioExampleHttp4sServer extends App {
  // extension methods for ZIO; not a strict requirement, but they make working with ZIO much nicer
  implicit class ZioEndpoint[I, E, O](e: Endpoint[I, E, O, EntityBody[Task]]) {
    def toZioRoutes(logic: I => IO[E, O])(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] = {
      import sttp.tapir.server.http4s._
      e.toRoutes(i => logic(i).either)
    }

    def zioServerLogic(logic: I => IO[E, O]): ServerEndpoint[I, E, O, EntityBody[Task], Task] = ServerEndpoint(e, logic(_).either)
  }
  case class Pet(species: String, url: String)

  import io.circe.generic.auto._
  import sttp.tapir.json.circe._

  // Simple Pet Endpoint
  val petEndpoint: Endpoint[Int, String, Pet, Nothing] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpRoutes[Task] = petEndpoint.toZioRoutes { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }

  // ZIO Endpoint with a custom Application Layer, implemented as ZLayer
  val zioEndpoint: Endpoint[Int, String, Pet, Nothing] =
    endpoint.get.in("zio" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val zioRoutes: HttpRoutes[Task] = zioEndpoint.toZioRoutes(petId => UserService.hello(petId).provideLayer(LayerEndpoint.liveEnv))

  // Final service is just a conjunction of different  Routes
  val service: HttpRoutes[Task] = petRoutes <+> zioRoutes

  // Or, using server logic:

  // Simple Pet Server Logic
  val petServerEndpoint = petEndpoint.zioServerLogic { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }

  val petServerRoutes: HttpRoutes[Task] = petServerEndpoint.toRoutes

  // Simple Pet Server Logic
  val zioServerEndpoint = zioEndpoint.zioServerLogic { petId => UserService.hello(petId).provideLayer(LayerEndpoint.liveEnv) }

  val zioServerRoutes: HttpRoutes[Task] = petServerEndpoint.toRoutes

  val service2: HttpRoutes[Task] = petServerRoutes <+> zioServerRoutes

  import sttp.tapir.docs.openapi._
  import sttp.tapir.openapi.circe.yaml._
  val yaml = List(petEndpoint).toOpenAPI("Our pets", "1.0").toYaml

  {
    val runtime = Runtime.default

    val serve = BlazeServerBuilder[Task]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> (service <+> new SwaggerHttp4s(yaml).routes[Task])).orNotFound)
      .serve
      .compile
      .drain

    runtime.unsafeRun(serve)
  }
}
