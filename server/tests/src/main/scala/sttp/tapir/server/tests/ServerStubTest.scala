package sttp.tapir.server.tests

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.stub4.TapirStubInterpreter

class ServerStubTest[F[_], R, OPTIONS](createStubServerTest: CreateServerStubTest[F, OPTIONS])
    extends AsyncFlatSpec
    with Matchers
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = createStubServerTest.cleanUp()

  val serverEp: Full[Unit, Unit, Unit, String, String, Any, F] = endpoint.get
    .in("greet")
    .out(stringBody)
    .errorOut(stringBody)
    .serverLogic(_ => createStubServerTest.stub.monad.unit(Right("hello from logic")))

  it should "stub endpoint logic" in {
    val server: Backend[F] =
      TapirStubInterpreter(createStubServerTest.customiseInterceptors, createStubServerTest.stub)
        .whenServerEndpoint(serverEp)
        .thenRespond("hello")
        .backend()

    val response = basicRequest.get(uri"http://test.com/greet").send(server)

    createStubServerTest.asFuture(response).map(_.body shouldBe Right("hello"))
  }
}
