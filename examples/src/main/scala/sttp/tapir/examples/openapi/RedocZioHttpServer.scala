package sttp.tapir.examples.openapi

import io.circe.generic.auto._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.redoc.bundle.RedocInterpreter
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir._
import zio.http.HttpApp
import zio.http.{Server, ServerConfig}
import zio.Console.{printLine, readLine}
import zio.{Task, ZIO, ZIOAppDefault}

object RedocZioHttpServer extends ZIOAppDefault {
  case class Pet(species: String, url: String)

  val petEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet]).zServerLogic { petId =>
      if (petId == 35) ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
      else ZIO.fail("Unknown pet id")
    }

  val petRoutes: HttpApp[Any, Throwable] = ZioHttpInterpreter().toHttp(petEndpoint)

  val redocRoutes: HttpApp[Any, Throwable] =
    ZioHttpInterpreter().toHttp(RedocInterpreter().fromServerEndpoints[Task](List(petEndpoint), "Our pets", "1.0"))

  val app = (petRoutes ++ redocRoutes).withDefaultErrorResponse

  override def run = {
    printLine("Go to: http://localhost:8080/docs") *>
      printLine("Press any key to exit ...") *>
      Server
        .serve(app)
        .provide(
          ServerConfig.live(ServerConfig.default.port(8080)),
          Server.live
        )
        .fork
        .flatMap { fiber =>
          readLine *> fiber.interrupt
        }
  }.exitCode
}
