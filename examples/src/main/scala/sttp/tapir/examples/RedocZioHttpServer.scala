package sttp.tapir.examples

import io.circe.generic.auto._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.redoc.Redoc
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir._
import zhttp.http.HttpApp
import zhttp.service.Server
import zio.console.{getStrLn, putStrLn}
import zio.{App, ExitCode, Task, URIO, ZIO}

object RedocZioHttpServer extends App {
  case class Pet(species: String, url: String)

  val petEndpoint: ZServerEndpoint[Any, Unit, Unit, Int, String, Pet, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet]).zServerLogic { petId =>
      if (petId == 35) ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
      else ZIO.fail("Unknown pet id")
    }

  val petRoutes: HttpApp[Any, Throwable] = ZioHttpInterpreter().toHttp(petEndpoint)

  val yaml: String = OpenAPIDocsInterpreter().toOpenAPI(petEndpoint, "Our pets", "1.0").toYaml
  val redocRoutes: HttpApp[Any, Throwable] = ZioHttpInterpreter().toHttp(Redoc[Task]("ZOO", yaml))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    putStrLn("Go to: http://localhost:8080/docs") *>
      putStrLn("Press any key to exit ...") *>
      Server.start(8080, petRoutes <> redocRoutes).fork.flatMap { fiber =>
        getStrLn *> fiber.interrupt
      }
  }.exitCode
}
