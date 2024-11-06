// {cat=Security; effects=ZIO; server=ZIO HTTP}: Separating security and server logic, with a reusable base endpoint, accepting & refreshing credentials via cookies

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.11.8
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-zio:3.10.1

package sttp.tapir.examples.security

import sttp.client3._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.model.HeaderNames
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir._
import zio.http.{Response => ZioHttpResponse, Routes, Server}
import zio.{Console, ExitCode, IO, Scope, Task, ZIO, ZIOAppDefault, ZLayer}

object ServerSecurityLogicZio extends ZIOAppDefault:
  // authentication data structure & logic
  case class User(name: String)
  case class AuthenticationToken(value: String)
  case class AuthenticationError(code: Int)

  def authenticate(token: AuthenticationToken): IO[AuthenticationError, User] =
    ZIO.succeed {
      if (token.value == "berries") ZIO.succeed(User("Papa Smurf"))
      else if (token.value == "smurf") ZIO.succeed(User("Gargamel"))
      else ZIO.fail(AuthenticationError(1001))
    }.flatten

  // defining a base endpoint, which has the authentication logic built-in
  val secureEndpoint: ZPartialServerEndpoint[Any, AuthenticationToken, User, Unit, AuthenticationError, Unit, Any] =
    endpoint
      .securityIn(auth.bearer[String]().mapTo[AuthenticationToken])
      // returning the authentication error code to the user
      .errorOut(plainBody[Int].mapTo[AuthenticationError])
      .zServerSecurityLogic(authenticate)

  // the errors that might occur in the /hello endpoint - either a wrapped authentication error, or refusal to greet
  sealed trait HelloError
  case class AuthenticationHelloError(wrapped: AuthenticationError) extends HelloError
  case class NoHelloError(why: String) extends HelloError

  // extending the base endpoint with hello-endpoint-specific inputs
  val secureHelloWorldWithLogic: ZServerEndpoint[Any, Any] = secureEndpoint.get
    .in("hello")
    .in(query[String]("salutation"))
    .out(stringBody)
    .mapErrorOut(AuthenticationHelloError.apply)(_.wrapped)
    // returning a 400 with the "why" field from the exception
    .errorOutVariant[HelloError](oneOfVariant(stringBody.mapTo[NoHelloError]))
    // defining the remaining server logic (which uses the authenticated user)
    .serverLogic { user => salutation =>
      ZIO
        .succeed(
          if (user.name == "Gargamel") ZIO.fail(NoHelloError(s"Not saying hello to ${user.name}!"))
          else ZIO.succeed(s"$salutation, ${user.name}!")
        )
        .flatten
    }

  // ---

  // interpreting as an app
  val routes: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(secureHelloWorldWithLogic)

  override def run: ZIO[Scope, Throwable, ExitCode] =
    def testWith(backend: SttpBackend[Task, Any], port: Int, path: String, salutation: String, token: String): Task[String] =
      basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:$port/$path?salutation=$salutation")
        .header(HeaderNames.Authorization, s"Bearer $token")
        .send(backend)
        .tap(result => Console.printLine(s"For path: $path, salutation: $salutation, token: $token, got result: $result"))
        .map(_.body)

    (for {
      port <- Server.install(routes)
      backend <- AsyncHttpClientZioBackend.scoped()
      _ <- testWith(backend, port, "hello", "Hello", "berries").map(r => assert(r == "Hello, Papa Smurf!"))
      _ <- testWith(backend, port, "hello", "Cześć", "berries").map(r => assert(r == "Cześć, Papa Smurf!"))
      _ <- testWith(backend, port, "hello", "Hello", "apple").map(r => assert(r == "1001"))
      _ <- testWith(backend, port, "hello", "Hello", "smurf").map(r => assert(r == "Not saying hello to Gargamel!"))
    } yield ()).exitCode
      .provideSome[Scope](
        ZLayer.succeed(Server.Config.default.port(8080)),
        Server.live
      )
