package sttp.tapir.examples

import io.circe.generic.auto._
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import zhttp.http.HttpApp
import zhttp.service.Server
import zio.{App, ExitCode, IO, Task, UIO, URIO, ZIO}

object ZioExampleZioHttpServer extends App {
  case class Pet(species: String, url: String)

  // Sample endpoint, with the logic implemented directly using .toRoutes
  val petEndpoint: PublicEndpoint[Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet])

  val petRoutes: HttpApp[Any, Throwable] =
    ZioHttpInterpreter().toHttp(
      petEndpoint.zServerLogic(petId =>
        if (petId == 35) ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
        else ZIO.fail("Unknown pet id")
      )
    )

  // Same as above, but combining endpoint description with server logic:
  val petServerEndpoint: ZServerEndpoint[Any, Any] = petEndpoint.zServerLogic { petId =>
    if (petId == 35) {
      UIO(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
    } else {
      IO.fail("Unknown pet id")
    }
  }
  val petServerRoutes: HttpApp[Any, Throwable] = ZioHttpInterpreter().toHttp(List(petServerEndpoint))

  //

  val swaggerRoutes: HttpApp[Any, Throwable] =
    ZioHttpInterpreter()
      .toHttp(SwaggerInterpreter().fromEndpoints[Task](List(petEndpoint), "Our pets", "1.0"))

  // Starting the server
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8080, petRoutes <> swaggerRoutes).exitCode
}
