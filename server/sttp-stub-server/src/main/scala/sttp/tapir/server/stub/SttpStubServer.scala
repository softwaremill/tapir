package sttp.tapir.server.stub

import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.testing.SttpBackendStub
import sttp.client.{NothingT, Request, Response, SttpBackend}
import sttp.model.StatusCode
import sttp.tapir.internal.SeqToParams
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues}
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults, ServerEndpoint}
import sttp.tapir.{DecodeFailure, Endpoint, EndpointInput}

trait SttpStubServer {

  implicit class RichStubServerEndpoints[F[+_]](
      endpoints: Iterable[ServerEndpoint[_, _, _, _, F]]
  ) {
    def toBackendStub(implicit me: MonadError[F]): SttpBackend[F, Nothing, NothingT] = {

      def pf[I](se: ServerEndpoint[I, _, _, _, F]): PartialFunction[Request[_, _], F[Response[_]]] =
        new PartialFunction[Request[_, _], F[Response[_]]] {
          val endpoint: Endpoint[_, _, _, _] = se.endpoint

          override def isDefinedAt(req: Request[_, _]): Boolean = {
            val decodeInputResult = DecodeInputs(endpoint.input, new SttpDecodeInput(req))
            decodeInputResult match {
              case DecodeInputsResult.Failure(input, failure) =>
                handleDecodeFailure(input, failure).code != StatusCode.NotFound
              case DecodeInputsResult.Values(_, _) => true
            }
          }

          override def apply(req: Request[_, _]): F[Response[_]] = {
            DecodeInputs(endpoint.input, new SttpDecodeInput(req)) match {
              case values: DecodeInputsResult.Values =>
                se.logic(SeqToParams(InputValues(se.endpoint.input, values)).asInstanceOf[I])
                  .map {
                    case Right(result) => Response(result, ServerDefaults.StatusCodes.success)
                    case Left(err)     => Response(err, StatusCode.InternalServerError)
                  }
              case DecodeInputsResult.Failure(input, failure) =>
                me.unit(handleDecodeFailure(input, failure))
            }
          }
        }

      val initialPf: PartialFunction[Request[_, _], F[Response[_]]] = PartialFunction.empty

      val matchers = endpoints.foldLeft(initialPf) {
        case (currentPf, endpoint) =>
          currentPf.orElse(pf(endpoint))
      }
      val clz = classOf[SttpBackendStub[F, Nothing]] // TODO reflection hack until stub constructor becomes public
      val ctor = clz.getDeclaredConstructor(
        classOf[MonadError[F]],
        classOf[PartialFunction[Request[_, _], F[Response[_]]]],
        classOf[Option[SttpBackend[F, Nothing, NothingT]]]
      )
      ctor.setAccessible(true)
      ctor.newInstance(me, matchers, None)
    }

    private def handleDecodeFailure(
        input: EndpointInput.Single[_],
        failure: DecodeFailure
    ): Response[_] = {
      val decodeFailureCtx = DecodeFailureContext(input, failure)
      val handling = ServerDefaults.decodeFailureHandler(decodeFailureCtx)
      handling match {
        case DecodeFailureHandling.NoMatch =>
          Response(failure.toString, StatusCode.NotFound)
        case DecodeFailureHandling.RespondWithResponse(_, value) =>
          Response(value, ServerDefaults.StatusCodes.error)
      }
    }
  }
}
