package sttp.tapir.server.stub4

import sttp.client4._
import sttp.model.{ContentTypeRange, HasHeaders, Header, Headers, Method, RequestMetadata, StatusCode, StatusText, Uri}
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.server.interpreter.{EncodeOutputs, OutputValues, ToResponseBody}
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.Charset
import scala.collection.immutable.Seq

private[stub4] object SttpResponseEncoder {
  def apply(output: EndpointOutput[_], responseValue: Any, statusCode: StatusCode): Response[Any] = {
    val outputValues: OutputValues[Any] =
      new EncodeOutputs[Any, AnyStreams](toResponseBody, Seq(ContentTypeRange.AnyRange))
        .apply(output, ParamsAsAny(responseValue), OutputValues.empty)

    Response(
      outputValues.body.map(_.apply(Headers(outputValues.headers))).getOrElse(()),
      outputValues.statusCode.getOrElse(statusCode),
      StatusText.default(outputValues.statusCode.getOrElse(statusCode)).getOrElse(""),
      outputValues.headers,
      Nil,
      new RequestMetadata {
        override def method: Method = Method.GET
        override def uri: Uri = uri"http://example.com"
        override def headers: Seq[Header] = Nil
      }
    )
  }

  val toResponseBody: ToResponseBody[Any, AnyStreams] = new ToResponseBody[Any, AnyStreams] {
    override val streams: AnyStreams = AnyStreams
    override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, AnyStreams]
    ): Any = pipe // impossible
  }
}
