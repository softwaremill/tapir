package sttp.tapir.examples

import cats.effect.*
import cats.syntax.all.*
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.client3.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.ExecutionContext

object ErrorUnionTypesHttp4sServer extends IOApp:
  // 0-cost wrapper for a security token
  opaque type Token = String
  object Token:
    def apply(t: String): Token = t

  // model of security & regular inputs/outputs
  case class AuthenticationFailure(reason: String)
  case class AuthenticationSuccess(userId: Int)
  case class LogicFailure(reason: String)
  case class LogicSuccess(message: String)

  // base endpoint with the structure of security defined; a blueprint for other endpoints
  val secureEndpoint: Endpoint[Token, Unit, AuthenticationFailure, Unit, Any] =
    endpoint
      .securityIn(auth.bearer[String]().map(Token(_))(_.toString))
      .errorOut(statusCode(StatusCode.Forbidden))
      .errorOut(jsonBody[AuthenticationFailure])

  // full endpoint, corresponds to: GET /hello/world?name=...; Authentication: Bearer ...
  val helloEndpoint: Endpoint[Token, String, AuthenticationFailure | LogicFailure, LogicSuccess, Any] =
    secureEndpoint.get
      .in("hello" / "world")
      .in(query[String]("name"))
      .errorOutVariant(oneOfVariant(jsonBody[LogicFailure]))
      .out(jsonBody[LogicSuccess])

  val helloServerEndpoint: ServerEndpoint[Any, IO] =
    helloEndpoint
      .serverSecurityLogicPure(token =>
        if token.toString.startsWith("secret")
        then Right(AuthenticationSuccess(token.length))
        else Left(AuthenticationFailure("wrong token"))
      )
      .serverLogicPure { authResult => name =>
        if name == "Gargamel"
        then Left(LogicFailure("wrong name"))
        else Right(LogicSuccess(s"Hello, $name (${authResult.userId})!"))
      }

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val helloWorldRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(helloServerEndpoint)

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

          def request(name: String, token: String) =
            val result = basicRequest
              .response(asStringAlways)
              .get(uri"http://localhost:8080/hello/world?name=$name")
              .auth
              .bearer(token)
              .send(backend)
            println(s"For $name and $token got body: ${result.body}, status code: ${result.code}")
            result

          assert(request("Papa Smurf", "secret123").body.contains("Hello, Papa Smurf (9)"))

          // by default, errors in the server logic correspond to status code 400
          assert(request("Gargamel", "secret123").body.contains("wrong name"))

          // will return the specified status code for authentication failures, 403
          assert(request("Papa Smurf", "hacker").body.contains("wrong token"))
        }
      }
      .as(ExitCode.Success)
