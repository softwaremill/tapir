package sttp.tapir.examples

import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.client._
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.tapir.server.http4s.ztapir._
import sttp.tapir.ztapir._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

object ZioPartialServerLogicHttp4s extends App {
  // authentication logic
  case class User(name: String)
  val AuthenticationErrorCode = 1001
  def auth(token: String): IO[Int, User] = {
    if (token == "secret") IO.succeed(User("Spock"))
    else IO.fail(AuthenticationErrorCode)
  }

  // 1st approach: define a base endpoint, which has the authentication logic built-in
  val secureEndpoint: ZPartialServerEndpoint[Any, User, Unit, Int, Unit] = endpoint
    .in(header[String]("X-AUTH-TOKEN"))
    .errorOut(plainBody[Int])
    .zServerLogicForCurrent(auth)

  // extend the base endpoint to define (potentially multiple) proper endpoints, define the rest of the server logic
  val secureHelloWorld1WithLogic = secureEndpoint.get
    .in("hello1")
    .in(query[String]("salutation"))
    .out(stringBody)
    .serverLogic { case (user, salutation) => IO.succeed(s"$salutation, ${user.name}!") }

  // ---

  // 2nd approach: define the endpoint entirely first
  val secureHelloWorld2: ZEndpoint[(String, String), Int, String] = endpoint
    .in(header[String]("X-AUTH-TOKEN"))
    .errorOut(plainBody[Int])
    .get
    .in("hello2")
    .in(query[String]("salutation"))
    .out(stringBody)

  // then, provide the server logic in parts
  val secureHelloWorld2WithLogic = secureHelloWorld2
    .zServerLogicPart(auth)
    .andThen { case (user, salutation) => IO.succeed(s"$salutation, ${user.name}!") }

  // ---

  // interpreting as routes
  val helloWorldRoutes: HttpRoutes[Task] = List(secureHelloWorld1WithLogic, secureHelloWorld2WithLogic).toRoutes

  // testing
  val test = AsyncHttpClientZioBackend.managed().use { backend =>
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

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO.runtime.flatMap { implicit runtime: Runtime[Any] =>
      // starting the server
      BlazeServerBuilder[Task](runtime.platform.executor.asEC)
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> helloWorldRoutes).orNotFound)
        .resource
        .use { _ =>
          test
        }
        .map(_ => ExitCode.success)
        .catchAll { t =>
          UIO {
            println("Exception when starting server")
            t.printStackTrace()
          }.map(_ => ExitCode.failure)
        }
    }
}
