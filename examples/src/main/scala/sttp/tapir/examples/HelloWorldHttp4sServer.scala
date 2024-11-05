// {cat=Hello, World!; effects=cats-effect; server=http4s}: Exposing an endpoint using the http4s server

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8
//> using dep org.http4s::http4s-blaze-server:0.23.16

package sttp.tapir.examples

import cats.effect.*
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.*
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext

object HelloWorldHttp4sServer extends IOApp:
  // the endpoint: single fixed path input ("hello"), single query parameter
  // corresponds to: GET /hello?name=...
  val helloWorld: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val helloWorldRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(helloWorld.serverLogic(name => IO(s"Hello, $name!".asRight[Unit])))

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def run(args: List[String]): IO[ExitCode] =
    // starting the server
    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> helloWorldRoutes).orNotFound)
      .resource
      .use { _ =>
        IO {
          val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
          val result: String = basicRequest.response(asStringAlways).get(uri"http://localhost:8080/hello?name=Frodo").send(backend).body
          println("Got result: " + result)
          assert(result == "Hello, Frodo!")
        }
      }
      .as(ExitCode.Success)
