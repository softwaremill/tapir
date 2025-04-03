package sttp.tapir.client.sttp4.ws

import sttp.capabilities.{Streams, WebSockets}
import sttp.client4._
import sttp.client4.ws.async
import sttp.model._
import sttp.tapir._
import sttp.tapir.client.ClientOutputParams
import sttp.tapir.client.sttp4.{EndpointToSttpClientBase, WebSocketEndpointToSttpClientExtensions, WebSocketToPipe}
import sttp.tapir.internal._
import sttp.ws.WebSocket
import sttp.tapir.client.sttp4.SttpClientOptions

private[sttp] class WebSocketEndpointToSttpClient[R <: Streams[_] with WebSockets](
    wsToPipe: WebSocketToPipe[R],
    clientOptions: SttpClientOptions
) extends EndpointToSttpClientBase
    with WebSocketEndpointToSttpClientExtensions {
  def toSttpRequest[F[_], A, E, O, I](
      e: Endpoint[A, I, E, O, R],
      baseUri: Option[Uri]
  ): A => I => WebSocketRequest[F, DecodeResult[Either[E, O]]] = { aParams => iParams =>
    // there can't be a stream body here, as this is not a stream endpoint
    val (reqWithInput, _) = prepareRequestWithInput(e, baseUri, aParams, iParams)

    if (bodyIsWebSocket(e.output)) {
      reqWithInput
        .response(
          async
            .asWebSocketEither(
              outToResponseAs(e.errorOutput, clientOptions),
              async.asWebSocketAlwaysUnsafe[F]
            )
            .map(_.merge)
            .mapWithMetadata(mapReqOutputWithMetadata(e, _, _, clientOutputParams))
            .map(mapDecodeError(_, reqWithInput))
        )
        .asInstanceOf[WebSocketRequest[F, DecodeResult[Either[E, O]]]]
    } else {
      throw new RuntimeException("Output body is not a WebSocket!")
    }
  }

  override protected def isSuccess(meta: ResponseMetadata): Boolean = meta.code == webSocketSuccessStatusCode

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
