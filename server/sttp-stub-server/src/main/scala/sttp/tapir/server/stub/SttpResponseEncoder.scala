package sttp.tapir.server.stub

import sttp.client3.Response
import sttp.model.{ContentTypeRange, HasHeaders, Headers, StatusCode}
import sttp.tapir.internal.{NoStreams, ParamsAsAny}
import sttp.tapir.server.interpreter.{EncodeOutputs, OutputValues, ToResponseBody}
import sttp.tapir.{CodecFormat, EndpointOutput, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.Charset
import scala.collection.immutable.Seq

private[stub] object SttpResponseEncoder {
  def apply(output: EndpointOutput[_], responseValue: Any, statusCode: StatusCode): sttp.client3.Response[Any] = {
    val outputValues: OutputValues[Any] =
      new EncodeOutputs[Any, NoStreams](toResponseBody, Seq(ContentTypeRange.AnyRange))
        .apply(output, ParamsAsAny(responseValue), OutputValues.empty)

    sttp.client3.Response(
      outputValues.body.map(_.apply(Headers(outputValues.headers))).getOrElse(()),
      outputValues.statusCode.getOrElse(statusCode),
      "",
      outputValues.headers,
      Nil,
      Response.ExampleGet
    )
  }

  private val toResponseBody: ToResponseBody[Any, NoStreams] = new ToResponseBody[Any, NoStreams] {
    override val streams: NoStreams = NoStreams
    override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
    ): Any = pipe // impossible
  }
}
