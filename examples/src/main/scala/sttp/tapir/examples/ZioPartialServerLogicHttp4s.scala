// {cat=Hello, World!; effects=ZIO; server=http4s}: Extending a base endpoint (which has the security logic provided), with server logic

//> using option -Ykind-projector
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server-zio:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio:1.11.8
//> using dep org.http4s::http4s-blaze-server:0.23.16
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-zio:3.10.1

package sttp.tapir.examples

import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.client3.*
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.tapir.examples.UserAuthenticationLayer.*
import sttp.tapir.server.http4s.ztapir.*
import sttp.tapir.ztapir.*
import zio.Console.printLine
import zio.*
import zio.interop.catz.*

object ZioPartialServerLogicHttp4s extends ZIOAppDefault:

  def greet(user: User, salutation: String): ZIO[Any, Nothing, String] = {
    val greeting = s"$salutation, ${user.name}!"
    printLine(greeting).as(greeting).orDie
  }

  // 1st approach: define a base endpoint, which has the authentication logic built-in
  val secureEndpoint: ZPartialServerEndpoint[UserService, String, User, Unit, Int, Unit, Any] = endpoint
    .securityIn(header[String]("X-AUTH-TOKEN"))
    .errorOut(plainBody[Int])
    .zServerSecurityLogic(UserService.auth)

  // extend the base endpoint to define (potentially multiple) proper endpoints, define the rest of the server logic
  val secureHelloWorld1WithLogic: ZServerEndpoint[UserService, Any] = secureEndpoint.get
    .in("hello1")
    .in(query[String]("salutation"))
    .out(stringBody)
    .serverLogic(u => s => greet(u, s))

  // ---

  // interpreting as routes
  val helloWorldRoutes: HttpRoutes[RIO[UserService, *]] =
    ZHttp4sServerInterpreter().from(List(secureHelloWorld1WithLogic)).toRoutes

  // testing
  val test: Task[Unit] = ZIO.scoped {
    AsyncHttpClientZioBackend.scoped().flatMap { backend =>
      def testWith(path: String, salutation: String, token: String): Task[String] =
        backend
          .send(
            basicRequest
              .response(asStringAlways)
              .get(uri"http://localhost:8080/$path?salutation=$salutation")
              .header("X-AUTH-TOKEN", token)
          )
          .map(_.body)
          .tap { result => Console.printLine(s"For path: $path, salutation: $salutation, token: $token, got result: $result") }

      def assertEquals(at: Task[String], b: String): Task[Unit] =
        at.flatMap { a =>
          if (a == b) ZIO.succeed(()) else ZIO.fail(new IllegalArgumentException(s"$a was not equal to $b"))
        }

      assertEquals(testWith("hello1", "Hello", "secret"), "Hello, Spock!") *>
        assertEquals(testWith("hello2", "Hello", "secret"), "Hello, Spock!") *>
        assertEquals(testWith("hello1", "Cześć", "secret"), "Cześć, Spock!") *>
        assertEquals(testWith("hello2", "Cześć", "secret"), "Cześć, Spock!") *>
        assertEquals(testWith("hello1", "Hello", "1234"), AuthenticationErrorCode.toString) *>
        assertEquals(testWith("hello2", "Hello", "1234"), AuthenticationErrorCode.toString)
    }
  }

  //

  override def run: URIO[Any, ExitCode] =
    ZIO.executor.flatMap(executor =>
      BlazeServerBuilder[RIO[UserService, *]]
        .withExecutionContext(executor.asExecutionContext)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> helloWorldRoutes).orNotFound)
        .resource
        .use(_ => test)
        .provide(UserService.live)
        .exitCode
    )
end ZioPartialServerLogicHttp4s

object UserAuthenticationLayer:
  type UserService = UserService.Service

  case class User(name: String)
  val AuthenticationErrorCode = 1001

  object UserService:
    trait Service:
      def auth(token: String): IO[Int, User]

    val live: ZLayer[Any, Nothing, Service] = ZLayer.succeed(new Service {
      def auth(token: String): IO[Int, User] = {
        if (token == "secret") ZIO.succeed(User("Spock"))
        else ZIO.fail(AuthenticationErrorCode)
      }
    })

    def auth(token: String): ZIO[UserService, Int, User] = ZIO.environmentWithZIO(_.get.auth(token))
  end UserService
