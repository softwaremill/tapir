package sttp.tapir.client

import sttp.tapir.{DecodeResult, Endpoint, PublicEndpoint}
import sttp.capabilities.Streams

package object sttp4 {
  private[sttp4] def throwDecodeFailures[T](dr: DecodeResult[T]): T =
    dr match {
      case DecodeResult.Value(v)    => v
      case DecodeResult.Error(_, e) => throw e
      case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
    }

  private[sttp4] def throwErrorExceptionMsg[I, E, O, R](endpoint: PublicEndpoint[I, E, O, R], i: I, e: E): String =
    s"Endpoint ${endpoint.show} returned error: $e, inputs: $i."

  private[sttp4] def throwErrorExceptionMsg[A, I, E, O, R](endpoint: Endpoint[A, I, E, O, R], a: A, i: I, e: E): String =
    s"Endpoint ${endpoint.show} returned error: $e, for security inputs: $a, inputs: $i."

  private[sttp4] type RequestStreamBody = (Streams[_], Any)
}
