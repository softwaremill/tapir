package sttp.tapir.server.interpreter

import sttp.model.headers.Accepts
import sttp.model.{ContentTypeRange, Headers, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.content.ContentTypeInterceptor
import sttp.tapir.server.interceptor.{EndpointInterceptor, ValuedEndpointOutput}
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput, StreamBodyIO}

import scala.collection.immutable.Seq

class ServerInterpreter[R, F[_]: MonadError, B, S](
    request: ServerRequest,
    requestBody: RequestBody[F, S],
    toResponseBody: ToResponseBody[B, S],
    interceptors: List[EndpointInterceptor[F, B]]
) {
  def apply(ses: List[ServerEndpoint[_, _, _, R, F]]): F[Option[ServerResponse[B]]] =
    ses match {
      case Nil => (None: Option[ServerResponse[B]]).unit
      case se :: tail =>
        apply(se).flatMap {
          case None => apply(tail)
          case r    => r.unit
        }
    }

  def apply[I, E, O](se: ServerEndpoint[I, E, O, R, F]): F[Option[ServerResponse[B]]] = {
    val ranges = Accepts.parse(request.headers)

    def valueToResponse(i: I): F[ServerResponse[B]] = {
      se.logic(implicitly)(i)
        .map {
          case Right(result) => outputToResponse(defaultSuccessStatusCode, se.endpoint.output, result, ranges)
          case Left(err)     => outputToResponse(defaultErrorStatusCode, se.endpoint.errorOutput, err, ranges)
        }
    }

    val decodedBasicInputs = DecodeBasicInputs(se.endpoint.input, request)

    decodeBody(decodedBasicInputs).flatMap {
      case values: DecodeBasicInputsResult.Values =>
        InputValue(se.endpoint.input, values) match {
          case InputValueResult.Value(params, _) =>
            callInterceptorsOnDecodeSuccess(
              new ContentTypeInterceptor[F, B](ranges) :: interceptors,
              se.endpoint,
              params.asAny.asInstanceOf[I],
              valueToResponse,
              ranges
            ).map(Some(_))
          case InputValueResult.Failure(input, failure) =>
            callInterceptorsOnDecodeFailure(interceptors, se.endpoint, input, failure, ranges)
        }
      case DecodeBasicInputsResult.Failure(input, failure) =>
        callInterceptorsOnDecodeFailure(interceptors, se.endpoint, input, failure, ranges)
    }
  }

  private def callInterceptorsOnDecodeSuccess[I](
      is: List[EndpointInterceptor[F, B]],
      endpoint: Endpoint[I, _, _, _],
      i: I,
      callLogic: I => F[ServerResponse[B]],
      ranges: Either[String, Seq[ContentTypeRange]]
  ): F[ServerResponse[B]] = is match {
    case Nil => callLogic(i)
    case interpreter :: tail =>
      interpreter.onDecodeSuccess(
        request,
        endpoint,
        i,
        {
          case None                                      => callInterceptorsOnDecodeSuccess(tail, endpoint, i, callLogic, ranges)
          case Some(ValuedEndpointOutput(output, value)) => outputToResponse(defaultSuccessStatusCode, output, value, ranges).unit
        }
      )
  }

  private def callInterceptorsOnDecodeFailure(
      is: List[EndpointInterceptor[F, B]],
      endpoint: Endpoint[_, _, _, _],
      failingInput: EndpointInput[_],
      failure: DecodeResult.Failure,
      ranges: Either[String, Seq[ContentTypeRange]]
  ): F[Option[ServerResponse[B]]] = is match {
    case Nil => Option.empty[ServerResponse[B]].unit
    case interpreter :: tail =>
      interpreter.onDecodeFailure(
        request,
        endpoint,
        failure,
        failingInput,
        {
          case None => callInterceptorsOnDecodeFailure(tail, endpoint, failingInput, failure, ranges)
          case Some(ValuedEndpointOutput(output, value)) =>
            (Some(outputToResponse(defaultErrorStatusCode, output, value, ranges)): Option[ServerResponse[B]]).unit
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
      defaultStatusCode: sttp.model.StatusCode,
      output: EndpointOutput[O],
      v: O,
      ranges: Either[String, Seq[ContentTypeRange]]
  ): ServerResponse[B] = {
    val outputValues =
      new EncodeOutputs(toResponseBody, ranges).apply(output, ParamsAsAny(v), OutputValues.empty)
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
