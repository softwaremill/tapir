package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.CodecFormat.OctetStream
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.ztapir.ZServerEndpoint
import sttp.tapir.{Codec, Endpoint, EndpointIO, EndpointInput, EndpointOutput, Mapping, Schema, StreamBodyIO, WebSocketBodyOutput}
import zio.stream.interop.fs2z._
import zio.{RIO, Task}

/** Converts server endpoints using ZioStreams to endpoints using Fs2Streams */
object ConvertStreams {

  def apply[R, C](
      se: ZServerEndpoint[R, ZioStreams with C]
  ): ServerEndpoint[Fs2Streams[RIO[R, *]] with C, RIO[R, *]] =
    ServerEndpoint(
      Endpoint(
        forInput(se.securityInput).asInstanceOf[EndpointInput[se.SECURITY_INPUT]],
        forInput(se.input).asInstanceOf[EndpointInput[se.INPUT]],
        forOutput(se.errorOutput).asInstanceOf[EndpointOutput[se.ERROR_OUTPUT]],
        forOutput(se.output).asInstanceOf[EndpointOutput[se.OUTPUT]],
        se.info
      ),
      se.securityLogic,
      se.logic
    )

  // the occasional casts are needed as we know that we return the same input as originally, but the compiler doesn't
  private def forInput(input: EndpointInput[_]): EndpointInput[_] = {
    input match {
      // streaming inputs
      case EndpointIO.StreamBodyWrapper(wrapped) => EndpointIO.StreamBodyWrapper(apply(wrapped))
      // traversing wrapped inputs
      case EndpointInput.Pair(left, right, combine, split) => EndpointInput.Pair(forInput(left), forInput(right), combine, split)
      case EndpointIO.Pair(left, right, combine, split)    =>
        EndpointIO.Pair(forInput(left).asInstanceOf[EndpointIO[_]], forInput(right).asInstanceOf[EndpointIO[_]], combine, split)
      case EndpointInput.MappedPair(wrapped, mapping) =>
        EndpointInput.MappedPair(forInput(wrapped).asInstanceOf[EndpointInput.Pair[_, _, Any]], mapping.asInstanceOf[Mapping[Any, Any]])
      case EndpointIO.MappedPair(wrapped, mapping) =>
        EndpointIO.MappedPair(forInput(wrapped).asInstanceOf[EndpointIO.Pair[_, _, Any]], mapping.asInstanceOf[Mapping[Any, Any]])
      case EndpointInput.Auth(wrapped, challenge, authType, info) =>
        EndpointInput.Auth(forInput(wrapped).asInstanceOf[EndpointInput.Single[_]], challenge, authType, info)
      case EndpointIO.OneOfBody(variants, mapping) =>
        EndpointIO.OneOfBody(
          variants.map {
            case EndpointIO.OneOfBodyVariant(range, Left(body)) =>
              EndpointIO.OneOfBodyVariant(range, Left(forInput(body).asInstanceOf[EndpointIO.Body[_, Any]]))
            case EndpointIO.OneOfBodyVariant(range, Right(body)) =>
              EndpointIO.OneOfBodyVariant(range, Right(forInput(body).asInstanceOf[EndpointIO.StreamBodyWrapper[_, Any]]))
          },
          mapping.asInstanceOf[Mapping[Any, Any]]
        )
      // all other cases - unchanged
      case _ => input
    }
  }

  private def forOutput(output: EndpointOutput[_]): EndpointOutput[_] = {
    output match {
      // streaming & ws outputs
      case EndpointIO.StreamBodyWrapper(wrapped)        => EndpointIO.StreamBodyWrapper(apply(wrapped))
      case EndpointOutput.WebSocketBodyWrapper(wrapped) => EndpointOutput.WebSocketBodyWrapper(apply(wrapped))
      // traversing wrapped outputs
      case EndpointOutput.Pair(left, right, combine, split) => EndpointOutput.Pair(forOutput(left), forOutput(right), combine, split)
      case EndpointIO.Pair(left, right, combine, split)     =>
        EndpointIO.Pair(forOutput(left).asInstanceOf[EndpointIO[_]], forOutput(right).asInstanceOf[EndpointIO[_]], combine, split)
      case EndpointOutput.MappedPair(wrapped, mapping) =>
        EndpointOutput.MappedPair(forOutput(wrapped).asInstanceOf[EndpointOutput.Pair[_, _, Any]], mapping.asInstanceOf[Mapping[Any, Any]])
      case EndpointIO.MappedPair(wrapped, mapping) =>
        EndpointIO.MappedPair(forOutput(wrapped).asInstanceOf[EndpointIO.Pair[_, _, Any]], mapping.asInstanceOf[Mapping[Any, Any]])
      case EndpointOutput.OneOf(mappings, mapping) =>
        EndpointOutput.OneOf[Any, Any](
          mappings.map(m => OneOfVariant(forOutput(m.output), m.appliesTo)),
          mapping.asInstanceOf[Mapping[Any, Any]]
        )
      case EndpointIO.OneOfBody(variants, mapping) =>
        EndpointIO.OneOfBody(
          variants.map {
            case EndpointIO.OneOfBodyVariant(range, Left(body)) =>
              EndpointIO.OneOfBodyVariant(range, Left(forOutput(body).asInstanceOf[EndpointIO.Body[_, Any]]))
            case EndpointIO.OneOfBodyVariant(range, Right(body)) =>
              EndpointIO.OneOfBodyVariant(range, Right(forOutput(body).asInstanceOf[EndpointIO.StreamBodyWrapper[_, Any]]))
          },
          mapping.asInstanceOf[Mapping[Any, Any]]
        )
      // all other cases - unchanged
      case _ => output
    }
  }

  private val fs2StreamsToZioStreamsCodec: Codec[fs2.Stream[Task, Byte], zio.stream.Stream[Throwable, Byte], OctetStream] =
    Codec
      .id[fs2.Stream[Task, Byte], OctetStream](OctetStream(), Schema.binary)
      .map(_.toZStream())(_.toFs2Stream)

  private def apply[R, BS, T, S](s: StreamBodyIO[BS, T, S]): StreamBodyIO[fs2.Stream[Task, Byte], T, Fs2Streams[RIO[R, *]]] = {
    // we know that BS == zio.stream.Stream[Throwable, Byte] and S == ZioStreams
    val s2 = s.asInstanceOf[StreamBodyIO[zio.stream.Stream[Throwable, Byte], T, ZioStreams]]
    StreamBodyIO(
      Fs2Streams[RIO[R, *]],
      fs2StreamsToZioStreamsCodec
        .mapDecode(s2.codec.decode)(s2.codec.encode)
        .schema(s2.codec.schema)
        .format(s2.codec.format),
      s2.info,
      s2.charset,
      s2.encodedExamples
    )
  }

  private def fs2PipeToZioPipeCodec[A, B]
      : Codec[fs2.Pipe[Task, A, B], zio.stream.Stream[Throwable, A] => zio.stream.Stream[Throwable, B], OctetStream] =
    Codec
      .id[fs2.Pipe[Task, A, B], OctetStream](OctetStream(), Schema.binary)
      .map { (fs2Pipe: fs2.Pipe[Task, A, B]) => (zioStreamA: zio.stream.Stream[Throwable, A]) =>
        fs2Pipe(zioStreamA.toFs2Stream).toZStream()
      } { (zioPipe: zio.stream.Stream[Throwable, A] => zio.stream.Stream[Throwable, B]) => (fs2StreamA: fs2.Stream[Task, A]) =>
        zioPipe(fs2StreamA.toZStream()).toFs2Stream
      }

  private def apply[R, PIPE_REQ_RESP, REQ, RESP, T, S](
      w: WebSocketBodyOutput[PIPE_REQ_RESP, REQ, RESP, T, S]
  ): WebSocketBodyOutput[fs2.Pipe[Task, REQ, RESP], REQ, RESP, T, Fs2Streams[RIO[R, *]]] = {
    // we know that:
    // * PIPE_REQ_RESP == zio.stream.Stream[Throwable, REQ] => zio.stream.Stream[Throwable, RESP]
    // * S == ZioStreams
    val w2 =
      w.asInstanceOf[WebSocketBodyOutput[zio.stream.Stream[Throwable, REQ] => zio.stream.Stream[Throwable, RESP], REQ, RESP, T, ZioStreams]]
    WebSocketBodyOutput(
      Fs2Streams[RIO[R, *]],
      w2.requests,
      w2.responses,
      fs2PipeToZioPipeCodec
        .mapDecode(w2.codec.decode)(w2.codec.encode)
        .schema(w2.codec.schema)
        .format(w2.codec.format),
      w2.info,
      w2.requestsInfo,
      w2.responsesInfo,
      w2.concatenateFragmentedFrames,
      w2.ignorePong,
      w2.autoPongOnPing,
      w2.decodeCloseRequests,
      w2.decodeCloseResponses,
      w2.autoPing
    )
  }
}
