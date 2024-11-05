// {cat=Testing; effects=cats-effect}: Test endpoints using the TapirStubInterpreter

//> using dep com.softwaremill.sttp.tapir::tapir-cats-effect:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-sttp-stub-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-cats:1.11.8
//> using dep com.softwaremill.sttp.client3::core:3.9.8
//> using dep org.scalatest::scalatest:3.2.19

package sttp.tapir.examples.testing

import cats.effect.IO
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.*
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.*
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.Future

class CatsServerStubInterpreter extends AsyncFlatSpec with Matchers:
  it should "run greet users logic" in {
    // given
    // We need to pass an SttpBackendStub which is configured for the IO effect. One way to do it is to pass a
    // MonadError implementation, as here. Alternatively, you can use any sttp-client cats-effect backend, and obtain
    // the stub using its .stub method. For example: AsyncHttpClientCatsBackend.stub[IO]
    val stubBackend: SttpBackend[IO, Any] = TapirStubInterpreter(SttpBackendStub[IO, Any](CatsMonadError()))
      .whenServerEndpoint(UsersApi.greetUser)
      .thenRunLogic()
      .backend()

    // when
    val response = sttp.client3.basicRequest
      .get(uri"http://test.com/api/users/greet")
      .header("Authorization", "Bearer password")
      .send(stubBackend)

    // then
    // since we are using ScalaTest, we need to run the IO effect, here - synchronously. When using an IO-aware test
    // framework, this might get simplified.
    import cats.effect.unsafe.implicits.global
    response.unsafeRunSync().body shouldBe Right("hello user123")
  }

  // The API under test
  object UsersApi:
    val greetUser: ServerEndpoint[Any, IO] = endpoint.get
      .in("api" / "users" / "greet")
      .securityIn(auth.bearer[String]())
      .out(stringBody)
      .errorOut(stringBody)
      .serverSecurityLogic(token => IO(if token == "password" then Right("user123") else Left("unauthorized")))
      .serverLogic(user => _ => IO(Right(s"hello $user")))
