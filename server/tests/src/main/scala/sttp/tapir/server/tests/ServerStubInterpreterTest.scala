package sttp.tapir.server.tests

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.Future

/** Test that check behaviour of stubbing interpreter with server specific `CustomInterceptors` */
abstract class ServerStubInterpreterTest[F[_], R, OPTIONS] extends AsyncFlatSpec with Matchers {

  def customInterceptors: CustomInterceptors[F, OPTIONS]
  def stub: SttpBackendStub[F, R]
  def asFuture[A]: F[A] => Future[A]

  val serverEp: Full[String, String, Unit, String, String, R, F] = endpoint.get
    .in("greet")
    .securityIn(auth.bearer[String]())
    .out(stringBody)
    .errorOut(stringBody)
    .serverSecurityLogic(token =>
      stub.responseMonad.unit {
        (if (token == "token123") Right("user123") else Left("unauthorized")): Either[String, String]
      }
    )
    .serverLogic(user => _ => stub.responseMonad.unit(Right(s"hello $user")))

  it should "use custom interceptors and stub endpoint logic" in {
    val server: SttpBackend[F, R] = TapirStubInterpreter[F, R, OPTIONS](customInterceptors, stub)
      .whenServerEndpoint(serverEp)
      .respond("hello")
      .backend()

    val response = sttp.client3.basicRequest
      .get(uri"http://test.com/greet")
      .header("Authorization", "Bearer token123")
      .send(server)

    asFuture(response).map(_.body shouldBe Right("hello"))
  }
}
