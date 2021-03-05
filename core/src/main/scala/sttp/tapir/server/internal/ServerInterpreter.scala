package sttp.tapir.server.internal

import sttp.model.{Header, Headers}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput, StreamBodyIO}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.{
  DecodeFailureContext,
  DecodeFailureHandler,
  DecodeFailureHandling,
  LogRequestHandling,
  ServerDefaults,
  ServerEndpoint
}

class ServerInterpreter[R, F[_]: MonadError, WB, B, S](
    requestBody: RequestBody[F, S],
    rawToResponseBody: ToResponseBody[WB, B, S],
    decodeFailureHandler: DecodeFailureHandler,
    logRequestHandling: LogRequestHandling[F[Unit]]
) {
  def apply(ses: List[ServerEndpoint[_, _, _, R, F]], request: ServerRequest): F[Option[ServerResponse[WB, B]]] =
    ses match {
      case Nil => (None: Option[ServerResponse[WB, B]]).unit
      case se :: tail =>
        apply(se, request).flatMap {
          case None => apply(tail, request)
          case r    => r.unit
        }
    }

  def apply[I, E, O](se: ServerEndpoint[I, E, O, R, F], request: ServerRequest): F[Option[ServerResponse[WB, B]]] = {
    def valueToResponse(value: Any): F[ServerResponse[WB, B]] = {
      val i = value.asInstanceOf[I]
      se.logic(implicitly)(i)
        .map {
          case Right(result) => outputToResponse(ServerDefaults.StatusCodes.success, se.endpoint.output, result)
          case Left(err)     => outputToResponse(ServerDefaults.StatusCodes.error, se.endpoint.errorOutput, err)
        }
        .flatMap { response => logRequestHandling.requestHandled(se.endpoint, response.code.code).map(_ => response) }
        .handleError { case e: Exception =>
          logRequestHandling.logicException(se.endpoint, e).flatMap(_ => implicitly[MonadError[F]].error(e))
        }
    }

    val decodedBasicInputs = DecodeBasicInputs(se.endpoint.input, request)

    decodeBody(decodedBasicInputs).flatMap {
      case values: DecodeBasicInputsResult.Values =>
        InputValue(se.endpoint.input, values) match {
          case InputValueResult.Value(params, _)        => valueToResponse(params.asAny).map(Some(_))
          case InputValueResult.Failure(input, failure) => handleDecodeFailure(se.endpoint, input, failure)
        }
      case DecodeBasicInputsResult.Failure(input, failure) => handleDecodeFailure(se.endpoint, input, failure)
    }
  }

  private def handleDecodeFailure(
      e: Endpoint[_, _, _, _],
      input: EndpointInput[_],
      failure: DecodeResult.Failure
  ): F[Option[ServerResponse[WB, B]]] = {
    val decodeFailureCtx = DecodeFailureContext(input, failure, e)
    val handling = decodeFailureHandler(decodeFailureCtx)
    handling match {
      case DecodeFailureHandling.NoMatch =>
        logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx).map(_ => None)
      case DecodeFailureHandling.RespondWithResponse(output, value) =>
        logRequestHandling
          .decodeFailureHandled(e, decodeFailureCtx, value)
          .map(_ => Some(outputToResponse(ServerDefaults.StatusCodes.error, output, value)))
    }
  }

  private def decodeBody(result: DecodeBasicInputsResult): F[DecodeBasicInputsResult] =
    result match {
      case values: DecodeBasicInputsResult.Values =>
        values.bodyInputWithIndex match {
          case Some((Left(bodyInput @ EndpointIO.Body(_, codec, _)), _)) =>
            requestBody.toRaw(bodyInput.bodyType).map { v =>
              codec.decode(v) match {
                case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
                case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
              }
            }

          case Some((Right(bodyInput @ EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, _))), _)) =>
            (codec.decode(requestBody.toStream()) match {
              case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
              case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
            }).unit

          case None => (values: DecodeBasicInputsResult).unit
        }
      case failure: DecodeBasicInputsResult.Failure => (failure: DecodeBasicInputsResult).unit
    }

  private def outputToResponse[O](defaultStatusCode: sttp.model.StatusCode, output: EndpointOutput[O], v: O): ServerResponse[WB, B] = {
    val outputValues = new EncodeOutputs(rawToResponseBody).apply(output, ParamsAsAny(v), OutputValues.empty)
    val statusCode = outputValues.statusCode.getOrElse(defaultStatusCode)

    val headers = outputValues.headers.map(p => Header(p._1, p._2))
    outputValues.body match {
      case Some(Left(bodyFromHeaders)) => ServerResponse(statusCode, headers, Some(Right(bodyFromHeaders(Headers(headers)))))
      case Some(Right(pipeF))          => ServerResponse(statusCode, headers, Some(Left(pipeF)))
      case None                        => ServerResponse(statusCode, headers, None)
    }
  }
}
