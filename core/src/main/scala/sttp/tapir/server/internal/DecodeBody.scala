package sttp.tapir.server.internal

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{DecodeResult, EndpointIO}

abstract class DecodeBody[REQUEST, F[_]: MonadError] {
  def apply(request: REQUEST, result: DecodeInputsResult): F[DecodeInputsResult] = {
    result match {
      case values: DecodeInputsResult.Values =>
        values.bodyInputWithIndex match {
          case Some((bodyInput @ EndpointIO.Body(_, codec, _), _)) =>
            rawBody(request, bodyInput).map { v =>
              codec.decode(v) match {
                case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
                case failure: DecodeResult.Failure => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
              }
            }

          case None => (values: DecodeInputsResult).unit
        }
      case failure: DecodeInputsResult.Failure => (failure: DecodeInputsResult).unit
    }
  }

  def rawBody[R](request: REQUEST, body: EndpointIO.Body[R, _]): F[R]
}
