// {cat=OpenAPI documentation; effects=ZIO; server=ZIO HTTP; json=circe; docs=ReDoc}: Exposing documentation using ReDoc

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-redoc-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.11.8

package sttp.tapir.examples.openapi

import io.circe.generic.auto.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.redoc.bundle.RedocInterpreter
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.{Response => ZioHttpResponse, Routes, Server}
import zio.Console.{printLine, readLine}
import zio.{Task, ZIO, ZIOAppDefault, ZLayer}

object RedocZioHttpServer extends ZIOAppDefault:
  case class Pet(species: String, url: String)

  val petEndpoint: ZServerEndpoint[Any, Any] =
    endpoint.get.in("pet" / path[Int]("petId")).errorOut(stringBody).out(jsonBody[Pet]).zServerLogic { petId =>
      if (petId == 35) ZIO.succeed(Pet("Tapirus terrestris", "https://en.wikipedia.org/wiki/Tapir"))
      else ZIO.fail("Unknown pet id")
    }

  val petRoutes: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(petEndpoint)

  val redocRoutes: Routes[Any, ZioHttpResponse] =
    ZioHttpInterpreter().toHttp(RedocInterpreter().fromServerEndpoints[Task](List(petEndpoint), "Our pets", "1.0"))

  val app: Routes[Any, ZioHttpResponse] = (petRoutes ++ redocRoutes)

  override def run = {
    printLine("Go to: http://localhost:8080/docs") *>
      printLine("Press any key to exit ...") *>
      Server
        .serve(app)
        .provide(
          ZLayer.succeed(Server.Config.default.port(8080)),
          Server.live
        )
        .fork
        .flatMap { fiber =>
          readLine *> fiber.interrupt
        }
  }.exitCode
