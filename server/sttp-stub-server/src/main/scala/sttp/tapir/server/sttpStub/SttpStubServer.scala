package sttp.tapir.server.sttpStub

import cats.effect.Sync
import sttp.client.monad.MonadError
import sttp.client.testing.SttpBackendStub
import sttp.client.{Request, Response}
import sttp.tapir.{DecodeFailure, Endpoint, EndpointInput}
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults}
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult}

trait SttpStubServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O, S, F[_]](e: Endpoint[I, E, O, S]) {
    def toRoutes(
        logic: I => F[Either[E, O]]
    )(implicit me: MonadError[F], serverOptions: SttpServerOptions[F], sync: Sync[F]): SttpBackendStub[F, S] = {
      val stuvb: SttpBackendStub[F, S] = SttpBackendStub(me)

      val pf = new PartialFunction[Request[_, _], Response[_]] {
        override def isDefinedAt(x: Request[_, _]): Boolean = {
          val decodeInputResult = DecodeInputs(e.input, new SttpDecodeInput(x))
          val handlingResult = decodeInputResult match {
            case DecodeInputsResult.Failure(input, failure) =>
              handleDecodeFailure(e, input, failure) != DecodeFailureHandling.noMatch
            case DecodeInputsResult.Values(_, _) => true
          }
          handlingResult
        }

        override def apply(v1: Request[_, _]): Response[_] = ???
      }

      stuvb.whenRequestMatchesPartial(pf)

      stuvb
    }

    private def handleDecodeFailure(
        e: Endpoint[_, _, _, _],
        input: EndpointInput.Single[_],
        failure: DecodeFailure
    )(implicit serverOptions: SttpServerOptions[F], sync: Sync[F]): F[Option[Response[F]]] = {
      val decodeFailureCtx = DecodeFailureContext(input, failure)
      val handling = serverOptions.decodeFailureHandler(decodeFailureCtx)
      handling match {
        case DecodeFailureHandling.NoMatch =>
          serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx).map(_ => None)
        case DecodeFailureHandling.RespondWithResponse(output, value) =>
          serverOptions.logRequestHandling
            .decodeFailureHandled(e, decodeFailureCtx, value)
            .map(_ => Some(outputToResponse(ServerDefaults.StatusCodes.error, output, value)))
      }
    }
  }
//  def toSttpRequestUnsafe(baseUri: Uri)(implicit clientOptions: SttpClientOptions): I => Request[Either[E, O], S] =
//    new EndpointToSttpClient(clientOptions).toSttpRequestUnsafe(e, baseUri)
//
//  def toSttpRequest(baseUri: Uri)(implicit clientOptions: SttpClientOptions): I => Request[DecodeResult[Either[E, O]], S] =
//    new EndpointToSttpClient(clientOptions).toSttpRequest(e, baseUri)
//
//  def toSttpRequestUnsafe[I, E, O, S](e: Endpoint[I, E, O, S], baseUri: Uri): I => Request[Either[E, O], S] = { params =>
//    toSttpRequest(e, baseUri)(params).mapResponse(getOrThrow)
//  }
//
//  def toSttpRequest[S, O, E, I](e: Endpoint[I, E, O, S], baseUri: Uri): I => Request[DecodeResult[Either[E, O]], S] = { params =>
//    val (uri, req1) =
//      setInputParams(
//        e.input.asVectorOfSingleInputs,
//        paramsTupleToParams(params),
//        0,
//        baseUri,
//        basicRequest.asInstanceOf[PartialAnyRequest]
//      )
//
//    val req2 = req1.copy[Identity, Any, Any](method = sttp.model.Method(e.input.method.getOrElse(Method.GET).method), uri = uri)
//
//    val responseAs = fromMetadata { meta =>
//      val output = if (meta.isSuccess) e.output else e.errorOutput
//      if (output == EndpointOutput.Void()) {
//        throw new IllegalStateException(s"Got response: $meta, cannot map to a void output of: $e.")
//      }
//
//      responseAsFromOutputs(meta, output)
//    }.mapWithMetadata { (body, meta) =>
//      val output = if (meta.isSuccess) e.output else e.errorOutput
//      val params = getOutputParams(output.asVectorOfSingleOutputs, body, meta)
//      params.map(p => if (meta.isSuccess) Right(p) else Left(p))
//    }
//
//    req2.response(responseAs).asInstanceOf[Request[DecodeResult[Either[E, O]], S]]
//  }
}
