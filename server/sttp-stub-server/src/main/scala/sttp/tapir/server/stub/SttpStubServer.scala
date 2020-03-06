package sttp.tapir.server.stub

import cats.FlatMap
import cats.effect.Effect
import cats.effect.syntax.effect._
import cats.syntax.applicative._
import cats.syntax.functor._
import sttp.client.monad.MonadError
import sttp.client.testing.SttpBackendStub
import sttp.client.{NothingT, Request, Response, SttpBackend}
import sttp.model.StatusCode
import sttp.tapir.internal.SeqToParams
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues}
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults, ServerEndpoint}
import sttp.tapir.{DecodeFailure, Endpoint, EndpointInput}

trait SttpStubServer {
  implicit class RichStubServerEndpoints[F[+_]: FlatMap: Effect](
      endpoints: Iterable[ServerEndpoint[_, _, _, _, F]]
  ) {
    def toBackendStub(implicit me: MonadError[F], serverOptions: SttpServerOptions[F]): SttpBackend[F, Nothing, NothingT] = {

      def pf[I](se: ServerEndpoint[I, _, _, _, F]) = new PartialFunction[Request[_, _], Response[_]] {
        val endpoint: Endpoint[_, _, _, _] = se.endpoint

        override def isDefinedAt(req: Request[_, _]): Boolean = {
          val decodeInputResult = DecodeInputs(endpoint.input, new SttpDecodeInput(req))
          decodeInputResult match {
            case DecodeInputsResult.Failure(input, failure) =>
              handleDecodeFailure(input, failure).code != StatusCode.NotFound
            case DecodeInputsResult.Values(_, _) => true
          }
        }

        override def apply(req: Request[_, _]): Response[_] = {
          val decodeInputResult = DecodeInputs(endpoint.input, new SttpDecodeInput(req))
          val effectfulResponse: F[Response[_]] = decodeInputResult match {
            case values: DecodeInputsResult.Values =>
              se.logic(SeqToParams(InputValues(se.endpoint.input, values)).asInstanceOf[I])
                .map {
                  case Right(result) => Response(result, ServerDefaults.StatusCodes.success)
                  case Left(err)     => Response(err, StatusCode.InternalServerError)
                }
            case DecodeInputsResult.Failure(input, failure) =>
              handleDecodeFailure(input, failure).pure[F]
          }
          effectfulResponse.toIO.unsafeRunSync()
        }
      }

      endpoints.foldLeft(SttpBackendStub(me)) {
        case (backendStub, endpoint) =>
          backendStub.whenRequestMatchesPartial(pf(endpoint))
      }
    }

    private def handleDecodeFailure(
        input: EndpointInput.Single[_],
        failure: DecodeFailure
    )(implicit serverOptions: SttpServerOptions[F]): Response[_] = {
      val decodeFailureCtx = DecodeFailureContext(input, failure)
      val handling = serverOptions.decodeFailureHandler(decodeFailureCtx)
      handling match {
        case DecodeFailureHandling.NoMatch =>
          Response(failure.toString, StatusCode.NotFound)
        case DecodeFailureHandling.RespondWithResponse(_, value) =>
          Response(value, ServerDefaults.StatusCodes.error)
      }
    }
  }
}
