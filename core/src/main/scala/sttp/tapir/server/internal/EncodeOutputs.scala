package sttp.tapir.server.internal

import java.nio.charset.Charset
import sttp.capabilities.{Effect, Streams}
import sttp.model.{HeaderNames, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.EndpointOutput.WebSocketBody
import sttp.tapir.internal.{Params, ParamsAsAny, SplitParams}
import sttp.tapir.{CodecFormat, EndpointIO, EndpointOutput, Mapping, RawBodyType}

import scala.util.Try

class EncodeOutputs[F[_], B, W, S](encodeOutputBody: EncodeOutputBody[B, W, S])(implicit monad: MonadError[F]) {
  def apply[R](output: EndpointOutput[_, R with Effect[F]], value: Params, ov: OutputValues[B, W]): F[OutputValues[B, W]] = {
    output match {
      case s: EndpointIO.Single[_, _]                 => applySingle(s, value, ov)
      case s: EndpointOutput.Single[_, _]             => applySingle(s, value, ov)
      case EndpointIO.Pair(left, right, _, split)     => applyPair(left, right, split, value, ov)
      case EndpointOutput.Pair(left, right, _, split) => applyPair(left, right, split, value, ov)
      case EndpointOutput.Void()                      => throw new IllegalArgumentException("Cannot encode a void output!")
    }
  }

  private def applyPair[R](
      left: EndpointOutput[_, R with Effect[F]],
      right: EndpointOutput[_, R with Effect[F]],
      split: SplitParams,
      params: Params,
      ov: OutputValues[B, W]
  ): F[OutputValues[B, W]] = {
    val (leftParams, rightParams) = split(params)
    apply(left, leftParams, ov).flatMap(l => apply(right, rightParams, l))
  }

  private def applySingle[R](
      output: EndpointOutput.Single[_, R with Effect[F]],
      value: Params,
      ov: OutputValues[B, W]
  ): F[OutputValues[B, W]] = {
    def encoded[T](mapping: Mapping[_, _]): T = mapping.asInstanceOf[Mapping[T, Any]].encode(value.asAny)
    output match {
      case EndpointIO.Empty(_, _)                   => ov.unit
      case EndpointOutput.FixedStatusCode(sc, _, _) => ov.withStatusCode(sc).unit
      case EndpointIO.FixedHeader(header, _, _)     => ov.withHeader(header.name -> header.value).unit
      case EndpointIO.Body(rawValueType, codec, _) =>
        ov.withBody(encodeOutputBody.rawValueToBody(encoded[Any](codec), codec.format, rawValueType)).unit
      case EndpointIO.StreamBody(_, codec, _, charset) =>
        ov.withBody(encodeOutputBody.streamValueToBody(encoded[encodeOutputBody.streams.BinaryStream](codec), codec.format, charset)).unit
      case EndpointIO.Header(name, codec, _) =>
        encoded[List[String]](codec).foldLeft(ov) { case (ovv, headerValue) => ovv.withHeader((name, headerValue)) }.unit
      case EndpointIO.Headers(codec, _) =>
        encoded[List[sttp.model.Header]](codec).foldLeft(ov)((ov2, h) => ov2.withHeader((h.name, h.value))).unit
      case EndpointIO.MappedPair(wrapped, mapping) => apply(wrapped, ParamsAsAny(encoded[Any](mapping)), ov)
      case EndpointOutput.StatusCode(_, codec, _)  => ov.withStatusCode(encoded[StatusCode](codec)).unit
      case o: EndpointOutput.WebSocketBody[_, _, _, _, _] =>
        ov.withWebSocketBody(
          encodeOutputBody.webSocketPipeToBody(
            encoded[encodeOutputBody.streams.Pipe[Any, Any]](o.codec),
            o.asInstanceOf[WebSocketBody[encodeOutputBody.streams.Pipe[Any, Any], Any, Any, Any, S]]
          )
        ).unit
      case EndpointOutput.OneOf(mappings, codec) =>
        val enc = encoded[Any](codec)
        val mapping = mappings
          .find(mapping => mapping.appliesTo(enc))
          .getOrElse(throw new IllegalArgumentException(s"No status code mapping for value: $enc, in output: $output"))
        apply(mapping.output, ParamsAsAny(enc), mapping.statusCode.map(ov.withStatusCode).getOrElse(ov))
      case EndpointOutput.MappedPair(wrapped, mapping) => apply(wrapped, ParamsAsAny(encoded[Any](mapping)), ov)
      case EndpointOutput.MapEffect(wrapped, _, g) =>
        g(monad).asInstanceOf[Any => F[Any]].apply(value.asAny).flatMap(v => apply(wrapped, ParamsAsAny(v), ov))
      case EndpointIO.MapEffect(wrapped, _, g) =>
        g(monad).asInstanceOf[Any => F[Any]].apply(value.asAny).flatMap(v => apply(wrapped, ParamsAsAny(v), ov))
    }
  }
}

case class OutputValues[B, W](
    body: Option[Either[B, W]],
    headers: Vector[(String, String)],
    statusCode: Option[StatusCode]
) {
  def withBody(b: B): OutputValues[B, W] = {
    if (body.isDefined) {
      throw new IllegalArgumentException("Body is already defined")
    }

    copy(body = Some(Left(b)))
  }

  def withWebSocketBody(w: W): OutputValues[B, W] = {
    if (body.isDefined) {
      throw new IllegalArgumentException("Body is already defined")
    }

    copy(body = Some(Right(w)))
  }

  def withHeader(h: (String, String)): OutputValues[B, W] = copy(headers = headers :+ h)

  def withStatusCode(sc: StatusCode): OutputValues[B, W] = copy(statusCode = Some(sc))

  def contentLength: Option[Long] =
    headers
      .collectFirst {
        case (k, v) if HeaderNames.ContentLength.equalsIgnoreCase(k) => v
      }
      .flatMap(v => Try(v.toLong).toOption)
}
object OutputValues {
  def empty[B, W]: OutputValues[B, W] = OutputValues[B, W](None, Vector.empty, None)
}

trait EncodeOutputBody[B, W, S] {
  val streams: Streams[S]
  def rawValueToBody[R](v: R, format: CodecFormat, bodyType: RawBodyType[R]): B
  def streamValueToBody(v: streams.BinaryStream, format: CodecFormat, charset: Option[Charset]): B
  def webSocketPipeToBody[REQ, RESP](pipe: streams.Pipe[REQ, RESP], o: WebSocketBody[streams.Pipe[REQ, RESP], REQ, RESP, _, S]): W
}
