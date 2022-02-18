package sttp.tapir.server.tests

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.stub.TapirStubInterpreter

class ServerStubInterpreterTest[F[_], R, OPTIONS](createStubServerTest: CreateServerStubTest[F, OPTIONS])
    extends AsyncFlatSpec
    with Matchers
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = createStubServerTest.cleanUp()

  val serverEp: Full[String, String, Unit, String, String, R, F] = endpoint.get
    .in("greet")
    .securityIn(auth.bearer[String]())
    .out(stringBody)
    .errorOut(stringBody)
    .serverSecurityLogic(token =>
      createStubServerTest.stub.responseMonad.unit {
        (if (token == "token123") Right("user123") else Left("unauthorized")): Either[String, String]
      }
    )
    .serverLogic(user => _ => createStubServerTest.stub.responseMonad.unit(Right(s"hello $user")))

  it should "stub endpoint logic" in {
    val server: SttpBackend[F, R] =
      TapirStubInterpreter[F, R, OPTIONS](createStubServerTest.customInterceptors, createStubServerTest.stub)
        .whenServerEndpoint(serverEp)
        .respond("hello")
        .backend()

    val response = sttp.client3.basicRequest
      .get(uri"http://test.com/greet")
      .header("Authorization", "Bearer token123")
      .send(server)

    createStubServerTest.asFuture(response).map(_.body shouldBe Right("hello"))
  }
}
