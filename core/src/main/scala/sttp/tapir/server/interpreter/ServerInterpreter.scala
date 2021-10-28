package sttp.tapir.server.interpreter

import sttp.model.{Headers, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.{Params, ParamsAsAny}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.{interceptor, _}
import sttp.tapir.{Codec, DecodeResult, EndpointIO, EndpointInput, StreamBodyIO, TapirFile}

class ServerInterpreter[R, F[_], B, S](
    requestBody: RequestBody[F, S],
    toResponseBody: ToResponseBody[B, S],
    interceptors: List[Interceptor[F]],
    deleteFile: TapirFile => F[Unit]
)(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]) {
  def apply[A, U, I, E, O](request: ServerRequest, se: ServerEndpoint[A, U, I, E, O, R, F]): F[RequestResult[B]] =
    apply(request, List(se))

  def apply(request: ServerRequest, ses: List[ServerEndpoint[_, _, _, _, _, R, F]]): F[RequestResult[B]] =
    monad.suspend(callInterceptors(interceptors, Nil, responder(defaultSuccessStatusCode), ses).apply(request))

  /** Accumulates endpoint interceptors and calls `next` with the potentially transformed request. */
  private def callInterceptors(
      is: List[Interceptor[F]],
      eisAcc: List[EndpointInterceptor[F]],
      responder: Responder[F, B],
      ses: List[ServerEndpoint[_, _, _, _, _, R, F]]
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
      ses: List[ServerEndpoint[_, _, _, _, _, R, F]],
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

  private def tryServerEndpoint[A, U, I, E, O](
      request: ServerRequest,
      se: ServerEndpoint[A, U, I, E, O, R, F],
      endpointInterceptors: List[EndpointInterceptor[F]]
  ): F[RequestResult[B]] = {
    val defaultSecurityFailureResponse = ServerResponse[B](StatusCode.InternalServerError, Nil, None).unit

    def endpointHandler(securityFailureResponse: => F[ServerResponse[B]]): EndpointHandler[F, B] =
      endpointInterceptors.foldRight(defaultEndpointHandler(securityFailureResponse)) { case (interceptor, handler) =>
        interceptor(responder(defaultSuccessStatusCode), handler)
      }

    def resultOrValueFrom = new ResultOrValueFrom {
      def onDecodeFailure(input: EndpointInput[_], failure: DecodeResult.Failure): F[RequestResult[B]] = {
        val decodeFailureContext = interceptor.DecodeFailureContext(input, failure, se.endpoint, request)
        endpointHandler(defaultSecurityFailureResponse)
          .onDecodeFailure(decodeFailureContext)
          .map {
            case Some(response) => RequestResult.Response(response)
            case None           => RequestResult.Failure(List(decodeFailureContext))
          }
      }
    }

    (for {
      // 1. decoding security inputs, computing the security input value, and decoding basic regular inputs
      // that way we will short-circuit further processing if any basic input fails to decode
      securityValues <- resultOrValueFrom(decodeBody(DecodeBasicInputs(se.endpoint.securityInput, request)))
      securityParams <- resultOrValueFrom(InputValue(se.endpoint.securityInput, securityValues))
      inputValues <- resultOrValueFrom(DecodeBasicInputs(se.endpoint.input, request))
      a = securityParams.asAny.asInstanceOf[A]
      // 2. running the security logic
      securityLogicResult <- ResultOrValue(
        se.securityLogic(monad)(a).map(Right(_): Either[RequestResult[B], Either[E, U]]).handleError { case t: Throwable =>
          endpointHandler(monad.error(t))
            .onSecurityFailure(SecurityFailureContext(se, a, request))
            .map(r => Left(RequestResult.Response(r)): Either[RequestResult[B], Either[E, U]])
        }
      )
      response <- securityLogicResult match {
        case Left(e) =>
          resultOrValueFrom.value(
            endpointHandler(responder(defaultErrorStatusCode)(request, ValuedEndpointOutput(se.endpoint.errorOutput, e)))
              .onSecurityFailure(SecurityFailureContext(se, a, request))
              .map(RequestResult.Response(_): RequestResult[B])
          )

        case Right(u) =>
          for {
            // 3. decoding the body of regular inputs, computing the input value, and running the main logic
            values <- resultOrValueFrom(decodeBody(inputValues))
            params <- resultOrValueFrom(InputValue(se.endpoint.input, values))
            response <- resultOrValueFrom.value(
              endpointHandler(defaultSecurityFailureResponse)
                .onDecodeSuccess(interceptor.DecodeSuccessContext(se, u, params.asAny.asInstanceOf[I], request))
                .map(RequestResult.Response(_): RequestResult[B])
            )
          } yield response
      }
    } yield response).fold
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
                    .foldLeft(monad.unit(()))((u, f) => u.flatMap(_ => deleteFile(f.file)))
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

  private def defaultEndpointHandler(securityFailureResponse: => F[ServerResponse[B]]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[U, I](
          ctx: DecodeSuccessContext[F, U, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        runLogic(ctx.serverEndpoint, ctx.u, ctx.i, ctx.request)

      private def runLogic[U, I, E, O](
          serverEndpoint: ServerEndpoint[_, U, I, E, O, _, F],
          u: U,
          i: I,
          request: ServerRequest
      ): F[ServerResponse[B]] =
        serverEndpoint
          .logic(implicitly)(u)(i)
          .flatMap {
            case Right(result) => responder(defaultSuccessStatusCode)(request, ValuedEndpointOutput(serverEndpoint.output, result))
            case Left(err)     => responder(defaultErrorStatusCode)(request, ValuedEndpointOutput(serverEndpoint.errorOutput, err))
          }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = securityFailureResponse

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

  private case class ResultOrValue[T](v: F[Either[RequestResult[B], T]]) {
    def flatMap[U](f: T => ResultOrValue[U]): ResultOrValue[U] = {
      ResultOrValue(v.flatMap {
        case Left(r)  => (Left(r): Either[RequestResult[B], U]).unit
        case Right(v) => f(v).v
      })
    }
    def map[U](f: T => U): ResultOrValue[U] = {
      ResultOrValue(v.map {
        case Left(r)  => Left(r): Either[RequestResult[B], U]
        case Right(v) => Right(f(v))
      })
    }
    def fold(implicit ev: T =:= RequestResult[B]): F[RequestResult[B]] = v.map {
      case Left(r)  => r
      case Right(r) => r
    }
  }

  private abstract class ResultOrValueFrom {
    def apply(v: F[DecodeBasicInputsResult]): ResultOrValue[DecodeBasicInputsResult.Values] = ResultOrValue(v.flatMap {
      case v: DecodeBasicInputsResult.Values               => (Right(v): Either[RequestResult[B], DecodeBasicInputsResult.Values]).unit
      case DecodeBasicInputsResult.Failure(input, failure) => onDecodeFailure(input, failure).map(Left(_))
    })
    def apply(v: InputValueResult): ResultOrValue[Params] = v match {
      case InputValueResult.Value(params, _)        => ResultOrValue((Right(params): Either[RequestResult[B], Params]).unit)
      case InputValueResult.Failure(input, failure) => ResultOrValue(onDecodeFailure(input, failure).map(Left(_)))
    }
    def apply(v: DecodeBasicInputsResult): ResultOrValue[DecodeBasicInputsResult.Values] = v match {
      case v: DecodeBasicInputsResult.Values =>
        ResultOrValue((Right(v): Either[RequestResult[B], DecodeBasicInputsResult.Values]).unit)
      case DecodeBasicInputsResult.Failure(input, failure) => ResultOrValue(onDecodeFailure(input, failure).map(Left(_)))
    }
    def value[T](v: F[T]): ResultOrValue[T] = ResultOrValue(v.map(Right(_)))

    def onDecodeFailure(input: EndpointInput[_], failure: DecodeResult.Failure): F[RequestResult[B]]
  }
}
