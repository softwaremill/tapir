package sttp.tapir.server.interpreter

import sttp.model.{Headers, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.{EndpointInterceptor, Interceptor, RequestInterceptor, ValuedEndpointOutput}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput, StreamBodyIO}

class ServerInterpreter[R, F[_]: MonadError, B, S](
    requestBody: RequestBody[F, S],
    toResponseBody: ToResponseBody[B, S],
    interceptors: List[Interceptor[F, B]]
) {
  def apply[I, E, O](request: ServerRequest, se: ServerEndpoint[R, F]): F[Option[ServerResponse[B]]] =
    apply(request, List(se))

  def apply(request: ServerRequest, ses: List[ServerEndpoint[R, F]]): F[Option[ServerResponse[B]]] =
    callInterceptors(interceptors, request, Nil, (request2, eis) => apply(request2, ses, eis))

  /** Accumulates endpoint interceptors and calls `next` with the potentially transformed request. */
  private def callInterceptors(
      is: List[Interceptor[F, B]],
      request: ServerRequest,
      eisAcc: List[EndpointInterceptor[F, B]],
      next: (ServerRequest, List[EndpointInterceptor[F, B]]) => F[Option[ServerResponse[B]]]
  ): F[Option[ServerResponse[B]]] = {
    is match {
      case Nil => next(request, eisAcc.reverse)
      case (i: RequestInterceptor[F, B]) :: tail =>
        i.onRequest(request, (request2, ei) => callInterceptors(tail, request2, ei :: eisAcc, next))
      case (ei: EndpointInterceptor[F, B]) :: tail => callInterceptors(tail, request, ei :: eisAcc, next)
    }
  }

  private def apply(
      request: ServerRequest,
      ses: List[ServerEndpoint[R, F]],
      endpointInterceptors: List[EndpointInterceptor[F, B]]
  ): F[Option[ServerResponse[B]]] =
    ses match {
      case Nil => (None: Option[ServerResponse[B]]).unit
      case se :: tail =>
        apply(request, se, endpointInterceptors).flatMap {
          case None => apply(request, tail, endpointInterceptors)
          case r    => r.unit
        }
    }

  private def apply(
      request: ServerRequest,
      se: ServerEndpoint[R, F],
      endpointInterceptors: List[EndpointInterceptor[F, B]]
  ): F[Option[ServerResponse[B]]] = {
    def valueToResponse(i: se.I): F[ServerResponse[B]] = {
      se.logic(implicitly)(i)
        .map {
          case Right(result) => outputToResponse(request, defaultSuccessStatusCode, se.endpoint.output, result)
          case Left(err)     => outputToResponse(request, defaultErrorStatusCode, se.endpoint.errorOutput, err)
        }
    }

    val decodedBasicInputs = DecodeBasicInputs(se.endpoint.input, request)

    decodeBody(decodedBasicInputs).flatMap {
      case values: DecodeBasicInputsResult.Values =>
        InputValue(se.endpoint.input, values) match {
          case InputValueResult.Value(params, _) =>
            callInterceptorsOnDecodeSuccess(request, endpointInterceptors, se.endpoint, params.asAny.asInstanceOf[se.I], valueToResponse)
              .map(Some(_))
          case InputValueResult.Failure(input, failure) =>
            callInterceptorsOnDecodeFailure(request, endpointInterceptors, se.endpoint, input, failure)
        }
      case DecodeBasicInputsResult.Failure(input, failure) =>
        callInterceptorsOnDecodeFailure(request, endpointInterceptors, se.endpoint, input, failure)
    }
  }

  private def callInterceptorsOnDecodeSuccess[I](
      request: ServerRequest,
      is: List[EndpointInterceptor[F, B]],
      endpoint: Endpoint[I, _, _, _],
      i: I,
      callLogic: I => F[ServerResponse[B]]
  ): F[ServerResponse[B]] = is match {
    case Nil => callLogic(i)
    case interpreter :: tail =>
      interpreter.onDecodeSuccess(
        request,
        endpoint,
        i,
        {
          case None                                      => callInterceptorsOnDecodeSuccess(request, tail, endpoint, i, callLogic)
          case Some(ValuedEndpointOutput(output, value)) => outputToResponse(request, defaultSuccessStatusCode, output, value).unit
        }
      )
  }

  private def callInterceptorsOnDecodeFailure(
      request: ServerRequest,
      is: List[EndpointInterceptor[F, B]],
      endpoint: Endpoint[_, _, _, _],
      failingInput: EndpointInput[_],
      failure: DecodeResult.Failure
  ): F[Option[ServerResponse[B]]] = is match {
    case Nil => Option.empty[ServerResponse[B]].unit
    case interpreter :: tail =>
      interpreter.onDecodeFailure(
        request,
        endpoint,
        failure,
        failingInput,
        {
          case None => callInterceptorsOnDecodeFailure(request, tail, endpoint, failingInput, failure)
          case Some(ValuedEndpointOutput(output, value)) =>
            (Some(outputToResponse(request, defaultErrorStatusCode, output, value)): Option[ServerResponse[B]]).unit
        }
      )
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

  private def outputToResponse[O](
      request: ServerRequest,
      defaultStatusCode: StatusCode,
      output: EndpointOutput[O],
      v: O
  ): ServerResponse[B] = {
    val outputValues =
      new EncodeOutputs(toResponseBody, request.acceptsContentTypes.getOrElse(Nil)).apply(output, ParamsAsAny(v), OutputValues.empty)
    val statusCode = outputValues.statusCode.getOrElse(defaultStatusCode)

    val headers = outputValues.headers
    outputValues.body match {
      case Some(bodyFromHeaders) => ServerResponse(statusCode, headers, Some(bodyFromHeaders(Headers(headers))))
      case None                  => ServerResponse(statusCode, headers, None)
    }
  }

  private val defaultSuccessStatusCode: StatusCode = StatusCode.Ok
  private val defaultErrorStatusCode: StatusCode = StatusCode.BadRequest
}
