package sttp.tapir.server.interceptor

import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint

/** The result of calling [[EndpointHandler.beforeDecode]] */
sealed trait BeforeDecodeResult[F[_], +B]
object BeforeDecodeResult {
  case class Response[F[_], B](response: ServerResponse[B]) extends BeforeDecodeResult[F, B]
  case class DecodeUsing[F[_]](request: ServerRequest, serverEndpoint: ServerEndpoint[_, _, _, _, F]) extends BeforeDecodeResult[F, Nothing]
}
