package sttp.tapir.server.interpreter

import sttp.model.{Headers, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.{ParamsAsAny, TapirFile}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.{interceptor, _}
import sttp.tapir.{Codec, DecodeResult, EndpointIO, EndpointInput, StreamBodyIO}

class ServerInterpreter[R, F[_], B, S](
    requestBody: RequestBody[F, S],
    toResponseBody: ToResponseBody[B, S],
    interceptors: List[Interceptor[F]],
    deleteFile: TapirFile => F[Unit]
)(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]) {
  def apply[I, E, O](request: ServerRequest, se: ServerEndpoint[I, E, O, R, F]): F[RequestResult[B]] =
    apply(request, List(se))

  def apply(request: ServerRequest, ses: List[ServerEndpoint[_, _, _, R, F]]): F[RequestResult[B]] =
    monad.suspend(callInterceptors(interceptors, Nil, responder(defaultSuccessStatusCode), ses).apply(request))

  /** Accumulates endpoint interceptors and calls `next` with the potentially transformed request. */
  private def callInterceptors(
      is: List[Interceptor[F]],
      eisAcc: List[EndpointInterceptor[F]],
      responder: Responder[F, B],
      ses: List[ServerEndpoint[_, _, _, R, F]]
  ): RequestHandler[F, B] = {
    is match {
      case Nil => RequestHandler.from { (request, _) => firstNotNone(request, ses, eisAcc.reverse, Nil) }
      case (i: RequestInterceptor[F]) :: tail =>
        i(
          responder,
          { ei => RequestHandler.from { (request, _) => callInterceptors(tail, ei :: eisAcc, responder, ses).apply(request) } }
        )
      case (ei: EndpointInterceptor[F]) :: tail => callInterceptors(tail, ei :: eisAcc, responder, ses)
    }
  }

  /** Try decoding subsequent server endpoints, until a non-None response is returned. */
  private def firstNotNone(
      request: ServerRequest,
      ses: List[ServerEndpoint[_, _, _, R, F]],
      endpointInterceptors: List[EndpointInterceptor[F]],
      accumulatedFailureContexts: List[DecodeFailureContext]
  ): F[RequestResult[B]] =
    ses match {
      case Nil => (RequestResult.Failure(accumulatedFailureContexts.reverse): RequestResult[B]).unit
      case se :: tail =>
        tryServerEndpoint(request, se, endpointInterceptors).flatMap {
          case RequestResult.Failure(failureContexts) =>
            firstNotNone(request, tail, endpointInterceptors, failureContexts ++: accumulatedFailureContexts)
          case r => r.unit
        }
    }

  private def tryServerEndpoint[I, E, O](
      request: ServerRequest,
      se: ServerEndpoint[I, E, O, R, F],
      endpointInterceptors: List[EndpointInterceptor[F]]
  ): F[RequestResult[B]] = {
    val decodedBasicInputs = DecodeBasicInputs(se.endpoint.input, request)

    def endpointHandler(defaultStatusCode: StatusCode): EndpointHandler[F, B] = endpointInterceptors.foldRight(defaultEndpointHandler) {
      case (interceptor, handler) => interceptor(responder(defaultStatusCode), handler)
    }

    def onDecodeFailure(input: EndpointInput[_], failure: DecodeResult.Failure): F[RequestResult[B]] = {
      val decodeFailureContext = interceptor.DecodeFailureContext(input, failure, se.endpoint, request)
      endpointHandler(defaultErrorStatusCode)
        .onDecodeFailure(decodeFailureContext)
        .map {
          case Some(response) => RequestResult.Response(response)
          case None           => RequestResult.Failure(List(decodeFailureContext))
        }
    }

    decodeBody(decodedBasicInputs).flatMap {
      case values: DecodeBasicInputsResult.Values =>
        InputValue(se.endpoint.input, values) match {
          case InputValueResult.Value(params, _) =>
            endpointHandler(defaultSuccessStatusCode)
              .onDecodeSuccess(interceptor.DecodeSuccessContext(se, params.asAny.asInstanceOf[I], request))
              .map(RequestResult.Response(_))
          case InputValueResult.Failure(input, failure) => onDecodeFailure(input, failure)
        }
      case DecodeBasicInputsResult.Failure(input, failure) => onDecodeFailure(input, failure)
    }
  }

  private def decodeBody(result: DecodeBasicInputsResult): F[DecodeBasicInputsResult] =
    result match {
      case values: DecodeBasicInputsResult.Values =>
        values.bodyInputWithIndex match {
          case Some((Left(bodyInput @ EndpointIO.Body(_, codec, _)), _)) =>
            requestBody.toRaw(bodyInput.bodyType).flatMap { v =>
              codec.decode(v.value) match {
                case DecodeResult.Value(bodyV) => (values.setBodyInputValue(bodyV): DecodeBasicInputsResult).unit
                case failure: DecodeResult.Failure =>
                  v.createdFiles
                    .foldLeft(monad.unit(()))((u, f) => u.flatMap(_ => deleteFile(f)))
                    .map(_ => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult)
              }
            }

          case Some((Right(bodyInput @ EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec: Codec[Any, Any, _], _, _))), _)) =>
            (codec.decode(requestBody.toStream()) match {
              case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
              case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
            }).unit

          case None => (values: DecodeBasicInputsResult).unit
        }
      case failure: DecodeBasicInputsResult.Failure => (failure: DecodeBasicInputsResult).unit
    }

  private val defaultEndpointHandler: EndpointHandler[F, B] = new EndpointHandler[F, B] {
    override def onDecodeSuccess[I](
        ctx: DecodeSuccessContext[F, I]
    )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
      runLogic(ctx.serverEndpoint, ctx.i, ctx.request)

    private def runLogic[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, _, F], i: I, request: ServerRequest): F[ServerResponse[B]] =
      serverEndpoint
        .logic(implicitly)(i)
        .flatMap {
          case Right(result) => responder(defaultSuccessStatusCode)(request, ValuedEndpointOutput(serverEndpoint.output, result))
          case Left(err)     => responder(defaultErrorStatusCode)(request, ValuedEndpointOutput(serverEndpoint.errorOutput, err))
        }

    override def onDecodeFailure(
        ctx: DecodeFailureContext
    )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] =
      (None: Option[ServerResponse[B]]).unit(monad)
  }

  private def responder(defaultStatusCode: StatusCode): Responder[F, B] = new Responder[F, B] {
    override def apply[O](request: ServerRequest, output: ValuedEndpointOutput[O]): F[ServerResponse[B]] = {
      val outputValues =
        new EncodeOutputs(toResponseBody, request.acceptsContentTypes.getOrElse(Nil))
          .apply(output.output, ParamsAsAny(output.value), OutputValues.empty)
      val statusCode = outputValues.statusCode.getOrElse(defaultStatusCode)

      val headers = outputValues.headers
      outputValues.body match {
        case Some(bodyFromHeaders) => ServerResponse(statusCode, headers, Some(bodyFromHeaders(Headers(headers)))).unit
        case None                  => ServerResponse(statusCode, headers, None: Option[B]).unit
      }
    }
  }

  private val defaultSuccessStatusCode: StatusCode = StatusCode.Ok
  private val defaultErrorStatusCode: StatusCode = StatusCode.BadRequest
}
