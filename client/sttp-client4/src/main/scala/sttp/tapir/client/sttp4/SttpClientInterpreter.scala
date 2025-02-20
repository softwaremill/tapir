package sttp.tapir.client.sttp4

import sttp.tapir.{DecodeResult, Endpoint, PublicEndpoint}

private[sttp4] trait SttpClientInterpreter {
  final protected def throwDecodeFailures[T](dr: DecodeResult[T]): T =
    dr match {
      case DecodeResult.Value(v)    => v
      case DecodeResult.Error(_, e) => throw e
      case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
    }

  final protected def throwErrorExceptionMsg[I, E, O, R](endpoint: PublicEndpoint[I, E, O, R], i: I, e: E): String =
    s"Endpoint ${endpoint.show} returned error: $e, inputs: $i."

  final protected def throwErrorExceptionMsg[A, I, E, O, R](endpoint: Endpoint[A, I, E, O, R], a: A, i: I, e: E): String =
    s"Endpoint ${endpoint.show} returned error: $e, for security inputs: $a, inputs: $i."

}
