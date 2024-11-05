// {cat=OpenAPI documentation; effects=cats-effect; server=http4s; docs=ReDoc}: Exposing documentation using ReDoc

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-redoc-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.8
//> using dep org.http4s::http4s-blaze-server:0.23.16

package sttp.tapir.examples.openapi

import cats.effect.*
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.tapir.*
import sttp.tapir.redoc.RedocUIOptions
import sttp.tapir.redoc.bundle.RedocInterpreter
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext

object RedocContextPathHttp4sServer extends IOApp:
  val contextPath: List[String] = List("api", "v1")
  val docPathPrefix: List[String] = "redoc" :: Nil

  val helloWorld: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("hello").in(query[String]("name")).out(stringBody)

  val routes: HttpRoutes[IO] =
    val redocEndpoints = RedocInterpreter(redocUIOptions = RedocUIOptions.default.contextPath(contextPath).pathPrefix(docPathPrefix))
      .fromEndpoints[IO](List(helloWorld), "The tapir library", "1.0.0")

    Http4sServerInterpreter[IO]().toRoutes(helloWorld.serverLogic(name => IO(s"Hello, $name!".asRight[Unit])) :: redocEndpoints)
  end routes

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def run(args: List[String]): IO[ExitCode] =
    // starting the server
    BlazeServerBuilder[IO]
      .withExecutionContext(ec)
      .bindHttp(8080, "localhost")
      .withHttpApp(Router(s"/${contextPath.mkString("/")}" -> routes).orNotFound)
      .resource
      .use { _ => IO.println(s"go to: http://127.0.0.1:8080/${(contextPath ++ docPathPrefix).mkString("/")}") *> IO.never }
      .as(ExitCode.Success)
