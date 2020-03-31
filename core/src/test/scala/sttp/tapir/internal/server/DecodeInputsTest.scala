package sttp.tapir.internal.server

import org.scalatest.{FlatSpec, Matchers}
import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsContext, DecodeInputsResult}
import sttp.tapir.{Codec, DecodeResult, EndpointIO, EndpointInput}

class DecodeInputsTest extends FlatSpec with Matchers {
  it should "return an error if decoding throws an exception" in {
    // given
    case class X(v: String)
    val e = new RuntimeException()
    implicit val xCodec: Codec[String, X, TextPlain] = Codec.string.map(_ => throw e)(_.v)
    val input = EndpointInput.Query[X]("x", implicitly, EndpointIO.Info(None, Nil, deprecated = false))

    // when & then
    DecodeInputs(input, StubDecodeInputContext) shouldBe DecodeInputsResult.Failure(input, DecodeResult.Error("v", e))
  }

  object StubDecodeInputContext extends DecodeInputsContext {
    override def method: Method = Method.GET
    override def nextPathSegment: (Option[String], DecodeInputsContext) = (None, this)
    override def header(name: String): List[String] = Nil
    override def headers: Seq[(String, String)] = Nil
    override def queryParameter(name: String): Seq[String] = List("v")
    override def queryParameters: MultiQueryParams = MultiQueryParams.fromMap(Map.empty)
    override def bodyStream: Any = ()
    override def serverRequest: ServerRequest = ???
  }
}
