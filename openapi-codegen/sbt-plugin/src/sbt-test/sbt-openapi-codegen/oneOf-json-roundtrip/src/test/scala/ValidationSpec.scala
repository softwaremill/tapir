import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.client.sttp.{SttpClientInterpreter, WebSocketToPipe}
import sttp.tapir.generated.TapirGeneratedEndpoints
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class ValidationSpec extends AnyFreeSpec with Matchers {
  val wsToPipe: WebSocketToPipe[Any] = WebSocketToPipe.webSocketsNotSupported[Any]
  val interpreter: SttpClientInterpreter = SttpClientInterpreter()

  val route = TapirGeneratedEndpoints.getPatternRestrictedObject
    .serverSecurityLogicSuccess[Unit, Future](_ => Future.successful(()))
    .serverLogic(_ => s => Future.successful(Right(s"OK! ${s.toString()}")))

  val stub = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
    .whenServerEndpoint(route)
    .thenRunLogic()
    .backend()

  type Params = (String, Option[String], String, Option[String], String)

  def runReq(params: Params) = interpreter
    .toSecureRequestThrowDecodeFailures(TapirGeneratedEndpoints.getPatternRestrictedObject, Some(uri"http://test.com/"))(wsToPipe)
    .apply("")(params)

  /// SUCCESSES
  def checkSuccess(v: Params) = {
    val req = runReq(v)
    val resp = Await.result(stub.send(req), 1.second)
    resp.code.code shouldEqual 200
    resp.body shouldEqual Right(s"OK! $v")
  }
  checkSuccess(("abcdef", Some("abc"), "abc", Some("abc"), "abc"))
  checkSuccess(("abcdef", Some("abc"), "abc", None, "abc"))
  checkSuccess(("abcdef", None, "abc", Some("abc"), "abc"))

  /// FAILURES
  def checkFailure(v: Params) = {
    val req = runReq(v)
    val resp = Await.result(stub.send(req), 1.second)
    resp.code.code shouldEqual 400
    resp.body shouldEqual Left(())
  }

  checkFailure(("abcdef", Some("abc"), "abc", Some("abc"), ""))
  checkFailure(("abcdef", Some("abc"), "abc", Some(""), "abc"))
  checkFailure(("abcdef", Some("abc"), "", Some("abc"), "abc"))
  checkFailure(("abcdef", Some(""), "abc", Some("abc"), "abc"))
  checkFailure(("c", Some("abc"), "abc", Some("abc"), "abc"))

}
