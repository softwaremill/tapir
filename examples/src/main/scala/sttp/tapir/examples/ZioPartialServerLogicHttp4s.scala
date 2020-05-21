package sttp.tapir.examples

import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import cats.implicits._
import sttp.client._
import sttp.tapir.server.http4s.ztapir._
import sttp.tapir.ztapir._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

object ZioPartialServerLogicHttp4s extends _root_.scala.App {
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
  val helloWorld1Routes: HttpRoutes[Task] = secureHelloWorld1WithLogic.toRoutes
  val helloWorld2Routes: HttpRoutes[Task] = secureHelloWorld2WithLogic.toRoutes

  // starting the server
  val check = Task {
    // testing
    implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

    def testWith(path: String, salutation: String, token: String): String = {
      val result: String = basicRequest
        .response(asStringAlways)
        .get(uri"http://localhost:8080/$path?salutation=$salutation")
        .header("X-AUTH-TOKEN", token)
        .send()
        .body

      println(s"For path: $path, salutation: $salutation, token: $token, got result: $result")
      result
    }

    assert(testWith("hello1", "Hello", "secret") == "Hello, Spock!")
    assert(testWith("hello2", "Hello", "secret") == "Hello, Spock!")
    assert(testWith("hello1", "Cześć", "secret") == "Cześć, Spock!")
    assert(testWith("hello2", "Cześć", "secret") == "Cześć, Spock!")
    assert(testWith("hello1", "Hello", "1234") == AuthenticationErrorCode.toString)
    assert(testWith("hello2", "Hello", "1234") == AuthenticationErrorCode.toString)
  }

  //

  implicit val runtime: Runtime[ZEnv] = Runtime.default

  val program = BlazeServerBuilder[Task](runtime.platform.executor.asEC)
    .bindHttp(8080, "localhost")
    .withHttpApp(Router("/" -> (helloWorld1Routes <+> helloWorld2Routes)).orNotFound)
    .resource
    .use { _ =>
      check
    }

  runtime.unsafeRun(program)
}
