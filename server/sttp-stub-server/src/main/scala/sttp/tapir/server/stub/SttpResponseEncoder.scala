package sttp.tapir.server.stub

import sttp.client3.Response
import sttp.model.{ContentTypeRange, HasHeaders, Headers, StatusCode, StatusText}
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.server.interpreter.{EncodeOutputs, OutputValues, ToResponseBody}
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.Charset
import scala.collection.immutable.Seq
import sttp.client3.testing.SttpBackendStub.RawStream

private[stub] object SttpResponseEncoder {
  def apply(output: EndpointOutput[_], responseValue: Any, statusCode: StatusCode): sttp.client3.Response[Any] = {
    val outputValues: OutputValues[Any] =
      new EncodeOutputs[Any, AnyStreams](toResponseBody, Seq(ContentTypeRange.AnyRange))
        .apply(output, ParamsAsAny(responseValue), OutputValues.empty)

    sttp.client3.Response(
      outputValues.body.map(_.apply(Headers(outputValues.headers))).getOrElse(()),
      outputValues.statusCode.getOrElse(statusCode),
      StatusText.default(outputValues.statusCode.getOrElse(statusCode)).getOrElse(""),
      outputValues.headers,
      Nil,
      Response.ExampleGet
    )
  }

  val toResponseBody: ToResponseBody[Any, AnyStreams] = new ToResponseBody[Any, AnyStreams] {
    override val streams: AnyStreams = AnyStreams
    override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any =
      RawStream(v)
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, AnyStreams]
    ): Any = pipe // impossible
  }
}
