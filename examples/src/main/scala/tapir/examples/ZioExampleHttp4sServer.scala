package tapir.examples

import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import scalaz.zio.interop.catz._
import scalaz.zio.interop.catz.implicits._
import scalaz.zio.{DefaultRuntime, IO, Task, UIO}
import tapir._
import tapir.server.ServerEndpoint
import tapir.server.http4s._

object ZioExampleHttp4sServer extends App {
  // extension methods for ZIO; not a strict requirement, but they make working with ZIO much nicer
  implicit class ZioEndpoint[I, E, O](e: Endpoint[I, E, O, EntityBody[Task]]) {
    def toZioRoutes(logic: I => IO[E, O])(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] = {
      import tapir.server.http4s._
      e.toRoutes(i => logic(i).either)
    }

    def zioServerLogic(logic: I => IO[E, O]): ServerEndpoint[I, E, O, EntityBody[Task], Task] = ServerEndpoint(e, logic(_).either)
  }

  //

  case class Pet(species: String, url: String)

  import io.circe.generic.auto._
  import tapir.json.circe._

  val petEndpoint: Endpoint[Int, String, Pet, Nothing] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val service: HttpRoutes[Task] = petEndpoint.toZioRoutes { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }

  // Or, using server logic:

  val petServerEndpoint = petEndpoint.zioServerLogic { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }

  val service2: HttpRoutes[Task] = petServerEndpoint.toRoutes

  //

  import tapir.docs.openapi._
  import tapir.openapi.circe.yaml._
  val yaml = List(petEndpoint).toOpenAPI("Our pets", "1.0").toYaml

  {
    implicit val runtime: DefaultRuntime = new DefaultRuntime {}

    val serve = BlazeServerBuilder[Task]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> service).orNotFound)
      .serve
      .compile
      .drain

    runtime.unsafeRun(serve)
  }
}
