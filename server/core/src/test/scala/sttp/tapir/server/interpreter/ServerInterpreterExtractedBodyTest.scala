package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.model.Method
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.TestUtil._
import sttp.tapir.server.interceptor.RequestResult

import java.nio.charset.StandardCharsets

class ServerInterpreterExtractedBodyTest extends AnyFlatSpec with Matchers {
  private implicit val idMonad: MonadError[Identity] = IdentityMonad

  private class CountingRequestBody(content: String) extends RequestBody[Identity, NoStreams] {
    var reads = 0
    override val streams: Streams[NoStreams] = NoStreams
    override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): RawValue[R] = {
      reads += 1
      RawValue(content.getBytes(StandardCharsets.UTF_8)).asInstanceOf[RawValue[R]]
    }
    override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
      throw new IllegalStateException("should not be called")
  }

  it should "decode the same request body for security and main logic, reading it once" in {
    val se = endpoint.post
      .in("test")
      .securityIn(extractBodyFromRequest(stringBody))
      .in(stringBody)
      .out(stringBody)
      .serverSecurityLogic[String, Identity](raw => Right(s"security:$raw"))
      .serverLogic(principal => body => Right(s"$principal|logic:$body"))

    val requestBody = new CountingRequestBody("payload")
    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(se),
      requestBody,
      StringToResponseBody,
      Nil,
      _ => ()
    )

    val result = interpreter.apply(createTestRequest(List("test"), _method = Method.POST))

    result shouldBe a[RequestResult.Response[?]]
    val response = result.asInstanceOf[RequestResult.Response[String]].response
    response.body shouldBe Some("security:payload|logic:payload")
    requestBody.reads shouldBe 1
  }

  it should "not read the body a second time when security logic fails" in {
    val se = endpoint.post
      .in("test")
      .securityIn(extractBodyFromRequest(stringBody))
      .in(stringBody)
      .out(stringBody)
      .errorOut(stringBody)
      .serverSecurityLogic[Unit, Identity](_ => Left("denied"))
      .serverLogic(_ => body => Right(body))

    val requestBody = new CountingRequestBody("payload")
    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(se),
      requestBody,
      StringToResponseBody,
      Nil,
      _ => ()
    )

    interpreter.apply(createTestRequest(List("test"), _method = Method.POST))
    requestBody.reads shouldBe 1
  }
}
