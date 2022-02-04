package sttp.tapir.examples

import org.http4s._
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.client3._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.tapir.examples.UserAuthenticationLayer._
import sttp.tapir.server.http4s.ztapir._
import sttp.tapir.ztapir._
import zio.Console.printLine
import zio._
import zio.interop.catz._

object ZioPartialServerLogicHttp4s extends ZIOAppDefault {

  def greet(user: User, salutation: String): ZIO[Console, Nothing, String] = {
    val greeting = s"$salutation, ${user.name}!"
    printLine(greeting).as(greeting).orDie
  }

  // 1st approach: define a base endpoint, which has the authentication logic built-in
  val secureEndpoint: ZPartialServerEndpoint[UserService, String, User, Unit, Int, Unit, Any] = endpoint
    .securityIn(header[String]("X-AUTH-TOKEN"))
    .errorOut(plainBody[Int])
    .zServerSecurityLogic(UserService.auth)

  // extend the base endpoint to define (potentially multiple) proper endpoints, define the rest of the server logic
  val secureHelloWorld1WithLogic = secureEndpoint.get
    .in("hello1")
    .in(query[String]("salutation"))
    .out(stringBody)
    .serverLogic(u => s => greet(u, s))

  // ---

  // interpreting as routes
  val helloWorldRoutes: HttpRoutes[RIO[UserService with Console with Clock, *]] =
    ZHttp4sServerInterpreter().from(List(secureHelloWorld1WithLogic)).toRoutes

  // testing
  val test: Task[Unit] = AsyncHttpClientZioBackend.managed().use { backend =>
    def testWith(path: String, salutation: String, token: String): Task[String] =
      backend
        .send(
          basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:8080/$path?salutation=$salutation")
            .header("X-AUTH-TOKEN", token)
        )
        .map(_.body)
        .tap { result => Task(println(s"For path: $path, salutation: $salutation, token: $token, got result: $result")) }

    def assertEquals(at: Task[String], b: String): Task[Unit] =
      at.flatMap { a =>
        if (a == b) Task.succeed(()) else Task.fail(new IllegalArgumentException(s"$a was not equal to $b"))
      }

    assertEquals(testWith("hello1", "Hello", "secret"), "Hello, Spock!") *>
      assertEquals(testWith("hello2", "Hello", "secret"), "Hello, Spock!") *>
      assertEquals(testWith("hello1", "Cześć", "secret"), "Cześć, Spock!") *>
      assertEquals(testWith("hello2", "Cześć", "secret"), "Cześć, Spock!") *>
      assertEquals(testWith("hello1", "Hello", "1234"), AuthenticationErrorCode.toString) *>
      assertEquals(testWith("hello2", "Hello", "1234"), AuthenticationErrorCode.toString)
  }

  //

  override def run =
    ZIO.runtime
      .flatMap { implicit runtime: Runtime[ZEnv & UserService & Console] =>
        BlazeServerBuilder[RIO[UserService & Console & Clock, *]]
          .withExecutionContext(runtime.runtimeConfig.executor.asExecutionContext)
          .bindHttp(8080, "localhost")
          .withHttpApp(Router("/" -> helloWorldRoutes).orNotFound)
          .resource
          .use { _ =>
            test
          }
      // starting
      }
      .provideCustomLayer(UserService.live)
      .exitCode
}

object UserAuthenticationLayer {
  type UserService = UserService.Service

  case class User(name: String)
  val AuthenticationErrorCode = 1001

  object UserService {
    trait Service {
      def auth(token: String): IO[Int, User]
    }

    val live: ZLayer[Any, Nothing, Service] = ZLayer.succeed(new Service {
      def auth(token: String): IO[Int, User] = {
        if (token == "secret") IO.succeed(User("Spock"))
        else IO.fail(AuthenticationErrorCode)
      }
    })

    def auth(token: String): ZIO[UserService, Int, User] = ZIO.environmentWithZIO(_.get.auth(token))
  }

}
