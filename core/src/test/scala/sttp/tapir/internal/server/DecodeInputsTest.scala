package sttp.tapir.internal.server

import sttp.model.Method
import sttp.tapir.{Codec, DecodeResult, EndpointIO, EndpointInput}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsContext, DecodeInputsResult}
import sttp.tapir.{Codec, DecodeResult, EndpointIO, EndpointInput}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DecodeInputsTest extends AnyFlatSpec with Matchers {
  it should "return an error if decoding throws an exception" in {
    // given
    case class X(v: String)
    val e = new RuntimeException()
    implicit val xCodec: PlainCodec[X] = Codec.stringPlainCodecUtf8.map(_ => throw e)(_.v)
    val input = EndpointInput.Query[X]("x", implicitly, EndpointIO.Info(None, None))

    // when & then
    DecodeInputs(input, StubDecodeInputContext) shouldBe DecodeInputsResult.Failure(input, DecodeResult.Error("List(v)", e))
  }

  object StubDecodeInputContext extends DecodeInputsContext {
    override def method: Method = Method.GET
    override def nextPathSegment: (Option[String], DecodeInputsContext) = (None, this)
    override def header(name: String): List[String] = Nil
    override def headers: Seq[(String, String)] = Nil
    override def queryParameter(name: String): Seq[String] = List("v")
    override def queryParameters: Map[String, Seq[String]] = Map.empty
    override def bodyStream: Any = ()
    override def serverRequest: ServerRequest = ???
  }
}
