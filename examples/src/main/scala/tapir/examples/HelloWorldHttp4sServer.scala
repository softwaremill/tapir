package tapir.examples

import cats.effect._
import com.softwaremill.sttp._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import tapir._
import tapir.server.http4s._
import cats.implicits._

import scala.concurrent.ExecutionContext

object HelloWorldHttp4sServer extends App {
  // the endpoint: single fixed path input ("hello"), single query parameter
  // corresponds to: GET /hello?name=...
  val helloWorld: Endpoint[String, Unit, String, Nothing] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  // mandatory implicits
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val helloWorldRoutes: HttpRoutes[IO] = helloWorld.toRoutes(name => IO(s"Hello, $name!".asRight[Unit]))

  // starting the server

  BlazeServerBuilder[IO]
    .bindHttp(8080, "localhost")
    .withHttpApp(Router("/" -> helloWorldRoutes).orNotFound)
    .resource
    .use { _ =>
      IO {
        implicit val backend: SttpBackend[com.softwaremill.sttp.Id, Nothing] = HttpURLConnectionBackend()
        val result: String = sttp.get(uri"http://localhost:8080/hello?name=Frodo").send().unsafeBody
        println("Got result: " + result)

        assert(result == "Hello, Frodo!")
      }
    }
    .unsafeRunSync()
}
