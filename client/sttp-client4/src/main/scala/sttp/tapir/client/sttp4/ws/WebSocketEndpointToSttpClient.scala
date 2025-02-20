package sttp.tapir.client.sttp4.ws

import sttp.capabilities.Streams
import sttp.client4._
import sttp.client4.ws.stream
import sttp.model._
import sttp.tapir._
import sttp.tapir.client.ClientOutputParams
import sttp.tapir.client.sttp4.{EndpointToSttpClient, EndpointToSttpClientExtensions, WebSocketToPipe}
import sttp.tapir.internal._
import sttp.ws.WebSocket

private[sttp] class WebSocketEndpointToSttpClient[S <: Streams[S]](wsToPipe: WebSocketToPipe[S])
    extends EndpointToSttpClient
    with EndpointToSttpClientExtensions {
  def toSttpRequest[F[_], A, E, O, I](
      e: Endpoint[A, I, E, O, S],
      baseUri: Option[Uri]
  ): A => I => WebSocketStreamRequest[DecodeResult[Either[E, O]], S] = { aParams => iParams =>
    val reqWithInput = prepareRequestWithInput(e, baseUri, aParams, iParams)

    if (bodyIsWebSocket(e.output)) {
      reqWithInput
        .response(
          stream
            .asWebSocketStream(wsToPipe.streams)(wsToPipe.pipe)
            .mapWithMetadata(mapReqOutputWithMetadata(e, _, _, clientOutputParams))
            .map(mapDecodeError(_, reqWithInput))
        )
        .asInstanceOf[WebSocketStreamRequest[DecodeResult[Either[E, O]], S]]
    } else {
      throw new RuntimeException("Output body is not a WebSocket!")
    }
  }

  private def bodyIsWebSocket[I](out: EndpointOutput[I]): Boolean = {
    out.traverseOutputs { case EndpointOutput.WebSocketBodyWrapper(_) =>
      Vector(())
    }.nonEmpty
  }

  private val clientOutputParams = new ClientOutputParams {
    override def decodeWebSocketBody(o: WebSocketBodyOutput[_, _, _, _, _], body: Any): DecodeResult[Any] = {
      val streams = o.streams.asInstanceOf[wsToPipe.S]
      o.codec
        .asInstanceOf[Codec[Any, _, CodecFormat]]
        .decode(
          wsToPipe
            .apply(streams)(
              body.asInstanceOf[WebSocket[wsToPipe.F]],
              o.asInstanceOf[WebSocketBodyOutput[Any, _, _, _, wsToPipe.S]]
            )
        )
    }
  }
}
