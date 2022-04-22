package sttp.tapir.server.interpreter

import sttp.model.{Headers, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.internal.{Params, ParamsAsAny, RichOneOfBody}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.{model, _}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.model.{ServerResponse, ValuedEndpointOutput}
import sttp.tapir.{DecodeResult, EndpointIO, EndpointInput, TapirFile}

class ServerInterpreter[R, F[_], B, S](
    serverEndpoints: ServerRequest => List[ServerEndpoint[R, F]],
    requestBody: RequestBody[F, S],
    toResponseBody: ToResponseBody[B, S],
    interceptors: List[Interceptor[F]],
    deleteFile: TapirFile => F[Unit]
)(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]) {
  def apply(request: ServerRequest): F[RequestResult[B]] = monad.suspend {
    callInterceptors(interceptors, Nil, responder(defaultSuccessStatusCode)).apply(request, serverEndpoints(request))
  }

  /** Accumulates endpoint interceptors and calls `next` with the potentially transformed request. */
  private def callInterceptors(
      is: List[Interceptor[F]],
      eisAcc: List[EndpointInterceptor[F]],
      responder: Responder[F, B]
  ): RequestHandler[F, R, B] = {
    is match {
      case Nil => RequestHandler.from { (request, ses, _) => firstNotNone(request, ses, eisAcc.reverse, Nil) }
      case (i: RequestInterceptor[F]) :: tail =>
        i(
          responder,
          { ei => RequestHandler.from { (request, ses, _) => callInterceptors(tail, ei :: eisAcc, responder).apply(request, ses) } }
        )
      case (ei: EndpointInterceptor[F]) :: tail => callInterceptors(tail, ei :: eisAcc, responder)
    }
  }

  /** Try decoding subsequent server endpoints, until a non-None response is returned. */
  private def firstNotNone(
      request: ServerRequest,
      ses: List[ServerEndpoint[R, F]],
      endpointInterceptors: List[EndpointInterceptor[F]],
      accumulatedFailureContexts: List[DecodeFailureContext]
  ): F[RequestResult[B]] =
    ses match {
      case Nil => (RequestResult.Failure(accumulatedFailureContexts.reverse): RequestResult[B]).unit
      case se :: tail =>
        tryServerEndpoint[se.SECURITY_INPUT, se.PRINCIPAL, se.INPUT, se.ERROR_OUTPUT, se.OUTPUT](
          request,
          se,
          endpointInterceptors
        )
          .flatMap {
            case RequestResult.Failure(failureContexts) =>
              firstNotNone(request, tail, endpointInterceptors, failureContexts ++: accumulatedFailureContexts)
            case r => r.unit
          }
    }

  private def tryServerEndpoint[A, U, I, E, O](
      request: ServerRequest,
      se: ServerEndpoint.Full[A, U, I, E, O, R, F],
      endpointInterceptors: List[EndpointInterceptor[F]]
  ): F[RequestResult[B]] = {
    val defaultSecurityFailureResponse =
      ServerResponse[B](StatusCode.InternalServerError, Nil, None, None).unit

    def endpointHandler(securityFailureResponse: => F[ServerResponse[B]]): EndpointHandler[F, B] =
      endpointInterceptors.foldRight(defaultEndpointHandler(securityFailureResponse)) { case (interceptor, handler) =>
        interceptor(responder(defaultSuccessStatusCode), handler)
      }

    def resultOrValueFrom = new ResultOrValueFrom {
      def onDecodeFailure(input: EndpointInput[_], failure: DecodeResult.Failure): F[RequestResult[B]] = {
        val decodeFailureContext = interceptor.DecodeFailureContext(se.endpoint, input, failure, request)
        endpointHandler(defaultSecurityFailureResponse)
          .onDecodeFailure(decodeFailureContext)
          .map {
            case Some(response) => RequestResult.Response(response)
            case None           => RequestResult.Failure(List(decodeFailureContext))
          }
      }
    }

    // 1. decoding both security & regular basic inputs - note that this does *not* include decoding the body
    val decodeBasicContext1 = DecodeInputsContext(request)
    // the security input doesn't have to match the whole path, a prefix is fine
    val (securityBasicInputs, decodeBasicContext2) =
      DecodeBasicInputs(se.endpoint.securityInput, decodeBasicContext1, matchWholePath = false)
    // the regular input is required to match the whole remaining path; otherwise a decode failure is reported
    // to keep the progress in path matching, we are using the context returned by decoding the security input
    val (regularBasicInputs, _) = DecodeBasicInputs(se.endpoint.input, decodeBasicContext2)
    (for {
      // 2. if the decoding failed, short-circuiting further processing with the decode failure that has a lower sort
      // index (so that the correct one is passed to the decode failure handler)
      _ <- resultOrValueFrom(DecodeBasicInputsResult.higherPriorityFailure(securityBasicInputs, regularBasicInputs))
      // 3. computing the security input value
      securityValues <- resultOrValueFrom(decodeBody(request, securityBasicInputs))
      securityParams <- resultOrValueFrom(InputValue(se.endpoint.securityInput, securityValues))
      inputValues <- resultOrValueFrom(regularBasicInputs)
      a = securityParams.asAny.asInstanceOf[A]
      // 4. running the security logic
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
            endpointHandler(responder(defaultErrorStatusCode)(request, model.ValuedEndpointOutput(se.endpoint.errorOutput, e)))
              .onSecurityFailure(SecurityFailureContext(se, a, request))
              .map(r => RequestResult.Response(r): RequestResult[B])
          )

        case Right(u) =>
          for {
            // 5. decoding the body of regular inputs, computing the input value, and running the main logic
            values <- resultOrValueFrom(decodeBody(request, inputValues))
            params <- resultOrValueFrom(InputValue(se.endpoint.input, values))
            response <- resultOrValueFrom.value(
              endpointHandler(defaultSecurityFailureResponse)
                .onDecodeSuccess(interceptor.DecodeSuccessContext(se, a, u, params.asAny.asInstanceOf[I], request))
                .map(r => RequestResult.Response(r): RequestResult[B])
            )
          } yield response
      }
    } yield response).fold
  }

  private def decodeBody(
      request: ServerRequest,
      result: DecodeBasicInputsResult
  ): F[DecodeBasicInputsResult] =
    result match {
      case values: DecodeBasicInputsResult.Values =>
        values.bodyInputWithIndex match {
          case Some((Left(oneOfBodyInput), _)) =>
            oneOfBodyInput.chooseBodyToDecode(request.contentTypeParsed) match {
              case Some(Left(body))                                          => decodeBody(request, values, body)
              case Some(Right(body: EndpointIO.StreamBodyWrapper[Any, Any])) => decodeStreamingBody(request, values, body)
              case None                                                      => unsupportedInputMediaTypeResponse(request, oneOfBodyInput)
            }
          case Some((Right(bodyInput: EndpointIO.StreamBodyWrapper[Any, Any]), _)) => decodeStreamingBody(request, values, bodyInput)
          case None                                                                => (values: DecodeBasicInputsResult).unit
        }
      case failure: DecodeBasicInputsResult.Failure => (failure: DecodeBasicInputsResult).unit
    }

  private def decodeStreamingBody(
      request: ServerRequest,
      values: DecodeBasicInputsResult.Values,
      bodyInput: EndpointIO.StreamBodyWrapper[Any, Any]
  ): F[DecodeBasicInputsResult] =
    (bodyInput.codec.decode(requestBody.toStream(request)) match {
      case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
      case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
    }).unit

  private def decodeBody[RAW, T](
      request: ServerRequest,
      values: DecodeBasicInputsResult.Values,
      bodyInput: EndpointIO.Body[RAW, T]
  ): F[DecodeBasicInputsResult] = {
    requestBody.toRaw(request, bodyInput.bodyType).flatMap { v =>
      bodyInput.codec.decode(v.value) match {
        case DecodeResult.Value(bodyV) => (values.setBodyInputValue(bodyV): DecodeBasicInputsResult).unit
        case failure: DecodeResult.Failure =>
          v.createdFiles
            .foldLeft(monad.unit(()))((u, f) => u.flatMap(_ => deleteFile(f.file)))
            .map(_ => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult)
      }
    }
  }

  private def unsupportedInputMediaTypeResponse(
      request: ServerRequest,
      oneOfBodyInput: EndpointIO.OneOfBody[_, _]
  ): F[DecodeBasicInputsResult] =
    (DecodeBasicInputsResult.Failure(
      oneOfBodyInput,
      DecodeResult
        .Mismatch(oneOfBodyInput.variants.map(_.range.toString()).mkString(", or: "), request.contentType.getOrElse(""))
    ): DecodeBasicInputsResult).unit

  private def defaultEndpointHandler(securityFailureResponse: => F[ServerResponse[B]]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[A, U, I](
          ctx: DecodeSuccessContext[F, A, U, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        ctx.serverEndpoint
          .logic(implicitly)(ctx.principal)(ctx.input)
          .flatMap {
            case Right(result) =>
              responder(defaultSuccessStatusCode)(ctx.request, model.ValuedEndpointOutput(ctx.serverEndpoint.output, result))
            case Left(err) =>
              responder(defaultErrorStatusCode)(ctx.request, model.ValuedEndpointOutput(ctx.serverEndpoint.errorOutput, err))
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
        case Some(bodyFromHeaders) => ServerResponse(statusCode, headers, Some(bodyFromHeaders(Headers(headers))), Some(output)).unit
        case None                  => ServerResponse(statusCode, headers, None: Option[B], Some(output)).unit
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
    def apply(f: Option[DecodeBasicInputsResult.Failure]): ResultOrValue[Unit] = f match {
      case None                                                  => ResultOrValue((Right(()): Either[RequestResult[B], Unit]).unit)
      case Some(DecodeBasicInputsResult.Failure(input, failure)) => ResultOrValue(onDecodeFailure(input, failure).map(Left(_)))
    }
    def value[T](v: F[T]): ResultOrValue[T] = ResultOrValue(v.map(Right(_)))

    def onDecodeFailure(input: EndpointInput[_], failure: DecodeResult.Failure): F[RequestResult[B]]
  }
}
