package sttp.tapir.client.sttp4

import sttp.client4._
import sttp.model._
import sttp.tapir._
import sttp.tapir.client.ClientOutputParams
import sttp.tapir.client.sttp4.{EndpointToSttpClientBase, SttpClientOptions}

private[sttp] class EndpointToSttpClient(clientOptions: SttpClientOptions) extends EndpointToSttpClientBase {
  def toSttpRequest[F[_], A, E, O, I](
      e: Endpoint[A, I, E, O, Any],
      baseUri: Option[Uri]
  ): A => I => Request[DecodeResult[Either[E, O]]] = { aParams => iParams =>
    val reqWithInput = prepareRequestWithInput(e, baseUri, aParams, iParams)

    val response = fromMetadata(
      outToResponseAs(e.errorOutput, clientOptions),
      ConditionalResponseAs(_.isSuccess, outToResponseAs(e.output, clientOptions))
    ).mapWithMetadata(mapReqOutputWithMetadata(e, _, _, clientOutputParams))
      .map(mapDecodeError(_, reqWithInput))

    reqWithInput.response(response).asInstanceOf[Request[DecodeResult[Either[E, O]]]]
  }

  private val clientOutputParams = new ClientOutputParams {
    override def decodeWebSocketBody(o: WebSocketBodyOutput[_, _, _, _, _], body: Any): DecodeResult[Any] =
      throw new RuntimeException("EndpointToSttpClient should not be used when dealing with WebSockets")
  }
}
