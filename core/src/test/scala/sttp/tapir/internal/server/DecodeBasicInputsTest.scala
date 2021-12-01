package sttp.tapir.internal.server

import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.interpreter.{DecodeBasicInputs, DecodeBasicInputsResult, DecodeInputsContext}
import sttp.tapir.{Codec, DecodeResult, EndpointIO, EndpointInput}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.Seq

class DecodeBasicInputsTest extends AnyFlatSpec with Matchers {
  it should "return an error if decoding throws an exception" in {
    // given
    case class X(v: String)
    val e = new RuntimeException()
    implicit val xCodec: Codec[String, X, TextPlain] = Codec.string.map(_ => throw e)(_.v)
    val input = EndpointInput.Query[X]("x", implicitly, EndpointIO.Info(None, Nil, deprecated = false, Vector.empty))

    // when & then
    DecodeBasicInputs(input, DecodeInputsContext(StubServerRequest))._1 shouldBe DecodeBasicInputsResult.Failure(
      input,
      DecodeResult.Error("v", e)
    )
  }

  object StubServerRequest extends ServerRequest {
    override def protocol: String = ???
    override def connectionInfo: ConnectionInfo = ???
    override def underlying: Any = ???
    override def pathSegments: List[String] = Nil
    override def queryParameters: QueryParams = QueryParams.fromMap(Map("x" -> "v"))
    override def method: Method = Method.GET
    override def uri: Uri = ???
    override def headers: Seq[Header] = Nil
  }
}
