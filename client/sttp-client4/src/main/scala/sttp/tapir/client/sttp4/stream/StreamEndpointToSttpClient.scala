package sttp.tapir.client.sttp4.stream

import sttp.capabilities.Streams
import sttp.client4._
import sttp.model._
import sttp.tapir._
import sttp.tapir.client.ClientOutputParams
import sttp.tapir.client.sttp4.EndpointToSttpClientBase
import sttp.tapir.internal._

private[sttp] class StreamEndpointToSttpClient[S <: Streams[S]](implicit ev: StreamsNotWebSockets[S]) extends EndpointToSttpClientBase {
  def toSttpRequest[A, E, O, I](
      e: Endpoint[A, I, E, O, S],
      baseUri: Option[Uri]
  ): A => I => StreamRequest[DecodeResult[Either[E, O]], S] = { aParams => iParams =>
    val reqWithInput = prepareRequestWithInput(e, baseUri, aParams, iParams)

    (bodyIsStream(e.input), bodyIsStream(e.output)) match {
      case (Some(streamsIn), None) => // request body is a stream
        reqWithInput
          .streamBody(streamsIn)(iParams.asInstanceOf[streamsIn.BinaryStream])
          .asInstanceOf[StreamRequest[DecodeResult[Either[E, O]], S]]
      case (None, Some(streamsOut)) => // response is a stream
        reqWithInput
          .response(
            asStreamAlwaysUnsafe(streamsOut)
              .mapWithMetadata(mapReqOutputWithMetadata(e, _, _, clientOutputParams))
              .map(mapDecodeError(_, reqWithInput))
          )
          .asInstanceOf[StreamRequest[DecodeResult[Either[E, O]], S]]
      case (Some(streamsIn), Some(streamsOut)) => { // both request body and response are streams
        reqWithInput
          .streamBody(streamsIn)(iParams.asInstanceOf[streamsIn.BinaryStream])
          .asInstanceOf[StreamRequest[Any, Any]]
          .response(
            asStreamAlwaysUnsafe(streamsOut)
              .mapWithMetadata(mapReqOutputWithMetadata(e, _, _, clientOutputParams))
              .map(mapDecodeError(_, reqWithInput))
          )
          .asInstanceOf[StreamRequest[DecodeResult[Either[E, O]], S]]
      }
      case (None, None) => throw new RuntimeException("Neither request body, nor response uses a stream")
    }
  }

  private def bodyIsStream[I](tr: EndpointTransput[I]): Option[Streams[_]] = {
    tr match {
      case out: EndpointOutput[_] =>
        out.traverseOutputs {
          case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _, _)) => Vector(streams)
          case EndpointIO.OneOfBody(variants, _) => variants.flatMap(_.body.toOption).map(_.wrapped.streams).toVector
        }.headOption
      case in: EndpointInput[_] =>
        in.traverseInputs {
          case EndpointIO.StreamBodyWrapper(StreamBodyIO(streams, _, _, _, _)) => Vector(streams)
          case EndpointIO.OneOfBody(variants, _) => variants.flatMap(_.body.toOption).map(_.wrapped.streams).toVector
        }.headOption
      case _ => None
    }
  }

  private val clientOutputParams = new ClientOutputParams {
    override def decodeWebSocketBody(o: WebSocketBodyOutput[_, _, _, _, _], body: Any): DecodeResult[Any] =
      throw new RuntimeException("StreamingEndpointToSttpClient should not be used when dealing with WebSockets")
  }
}
