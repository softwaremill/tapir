import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.Endpoint
import sttp.tapir.client.sttp.{SttpClientInterpreter, WebSocketToPipe}
import sttp.tapir.generated.TapirGeneratedEndpoints
import sttp.tapir.generated.TapirGeneratedEndpoints.{ValidatedObj, ValidatedOneOfA, ValidatedOneOfB, ValidatedOneOfC, ValidatedSubObj}
import sttp.tapir.generated.TapirGeneratedEndpointsValidators.ValidatedObjMapValidator
import sttp.tapir.server.stub.TapirStubInterpreter

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class ValidationSpec extends AnyFreeSpec with Matchers {
  val wsToPipe: WebSocketToPipe[Any] = WebSocketToPipe.webSocketsNotSupported[Any]
  val interpreter: SttpClientInterpreter = SttpClientInterpreter()

  private def stubForEndpoint[I, O](e: Endpoint[String, I, Unit, O, Any], res: I => O) = {
    val route = e
      .serverSecurityLogicSuccess[Unit, Future](_ => Future.successful(()))
      .serverLogic(_ => s => Future.successful(Right(res(s))))
    TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(route)
      .thenRunLogic()
      .backend()
  }

  "path and param validation" in {
    type Params = (String, Option[String], String, Option[String], String)
    val stub = stubForEndpoint[Params, String](TapirGeneratedEndpoints.getPatternRestrictedObject, s => s"OK! ${s.toString()}")
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
  "schema validation" in {
    type Params = Option[ValidatedObj]
    val stub = stubForEndpoint[Params, Unit](TapirGeneratedEndpoints.postValidationTest, _ => ())
    def runReq(params: Params) = interpreter
      .toSecureRequestThrowDecodeFailures(TapirGeneratedEndpoints.postValidationTest, Some(uri"http://test.com/"))(wsToPipe)
      .apply("")(params)
    /// SUCCESSES
    def checkSuccess(v: Params) = {
      val req = runReq(v)
      val resp = Await.result(stub.send(req), 1.second)
      resp.code.code shouldEqual 204
      resp.body shouldEqual Right(())
    }
    checkSuccess(None)
    val minimalValidObj =
      ValidatedObj(oneOf = None, bar = ValidatedSubObj("iiii4"), foo = "", map = None, arr = None, quux = None, baz = None)
    checkSuccess(Some(minimalValidObj))
    checkSuccess(Some(minimalValidObj.copy(oneOf = Some(ValidatedOneOfA("i1")))))
    checkSuccess(Some(minimalValidObj.copy(oneOf = Some(ValidatedOneOfB(Some(0))))))
    checkSuccess(Some(minimalValidObj.copy(oneOf = Some(ValidatedOneOfC(None)))))
    checkSuccess(Some(minimalValidObj.copy(map = Some(Map("a" -> 1, "b" -> 2, "c" -> 2)))))
    checkSuccess(Some(minimalValidObj.copy(arr = Some(Seq(3, 6, 9)))))
    checkSuccess(Some(minimalValidObj.copy(baz = Some(ValidatedSubObj("i1")))))
    checkSuccess(Some(minimalValidObj.copy(set = Some(Set("i1", "hi")))))
    /// FAILURES
    def checkFailure(v: Params) = {
      val req = runReq(v)
      val resp = Await.result(stub.send(req), 1.second)
      resp.code.code shouldEqual 400
      resp.body shouldEqual Left(())
    }
    checkFailure(Some(ValidatedObj(None, ValidatedSubObj(""), "", None, None, None, None)))
    checkFailure(Some(minimalValidObj.copy(oneOf = Some(ValidatedOneOfA("i")))))
    checkFailure(Some(minimalValidObj.copy(oneOf = Some(ValidatedOneOfB(Some(-1))))))
    checkFailure(Some(minimalValidObj.copy(map = Some(Map("a" -> 1, "b" -> 2, "c" -> -1)))))
    checkFailure(Some(minimalValidObj.copy(map = Some(Map("a" -> 1, "b" -> 2, "c" -> 900)))))
    checkFailure(Some(minimalValidObj.copy(map = Some(Map("a" -> 1, "b" -> 2)))))
    checkFailure(Some(minimalValidObj.copy(map = Some((1 to 13).map(i => i.toBinaryString -> i).toMap))))
    checkFailure(Some(minimalValidObj.copy(arr = Some((1 to 13).map(_ => 3)))))
    checkFailure(Some(minimalValidObj.copy(arr = Some(Seq(2, 6, 9)))))
    checkFailure(Some(minimalValidObj.copy(arr = Some(Seq(0)))))
    checkFailure(Some(minimalValidObj.copy(arr = Some(Seq(15)))))
    checkFailure(Some(minimalValidObj.copy(baz = Some(ValidatedSubObj("1")))))
    checkFailure(Some(minimalValidObj.copy(set = Some((1 to 20).map(_.toString).toSet))))
  }

}
