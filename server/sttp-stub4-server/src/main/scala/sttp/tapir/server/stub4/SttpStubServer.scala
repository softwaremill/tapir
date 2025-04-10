package sttp.tapir.server.stub4

import sttp.client4.Response
import sttp.client4.testing._
import sttp.model._
import sttp.shared.Identity
import sttp.tapir.server.interpreter._
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}
import sttp.capabilities.WebSockets

trait SttpStubServer {

  implicit class RichSyncBackendStub(stub: SyncBackendStub) extends AbstractRichBackendStub[Identity, Any, SyncBackendStub](stub) {}

  implicit class RichBackendStub[F[_]](stub: BackendStub[F]) extends AbstractRichBackendStub[F, Any, BackendStub[F]](stub) {}

  implicit class RichStreamBackendStub[F[_], S](stub: StreamBackendStub[F, S])
      extends AbstractRichBackendStub[F, S, StreamBackendStub[F, S]](stub) {}

  implicit class RichWebSocketBackendStub[F[_]](stub: WebSocketBackendStub[F])
      extends AbstractRichBackendStub[F, WebSockets, WebSocketBackendStub[F]](stub) {}

  implicit class RichWebSocketStreamBackendStub[F[_], S](stub: WebSocketStreamBackendStub[F, S])
      extends AbstractRichBackendStub[F, WebSockets with S, WebSocketStreamBackendStub[F, S]](stub) {}

  //

  abstract class AbstractRichBackendStub[F[_], R, STUB <: AbstractBackendStub[F, R] { type Self = STUB }](val stub: STUB) {
    def whenRequestMatchesEndpoint[E, O](endpoint: Endpoint[_, _, E, O, _]): EndpointWhenRequest[_, _, E, O] =
      new EndpointWhenRequest(
        endpoint,
        new stub.WhenRequest(req =>
          DecodeBasicInputs(endpoint.input, DecodeInputsContext(new SttpRequest(req))) match {
            case (_: DecodeBasicInputsResult.Failure, _) => false
            case (_: DecodeBasicInputsResult.Values, _)  => true
          }
        )
      )

    def whenSecurityInputMatches[A, I, E, O](endpoint: Endpoint[A, I, E, O, _])(
        securityInputMatcher: A => Boolean
    ): EndpointWhenRequest[A, I, E, O] =
      new EndpointWhenRequest(endpoint, whenInputMatches(endpoint.securityInput)(securityInputMatcher))

    def whenInputMatches[A, I, E, O](endpoint: Endpoint[A, I, E, O, _])(inputMatcher: I => Boolean): EndpointWhenRequest[A, I, E, O] =
      new EndpointWhenRequest(endpoint, whenInputMatches(endpoint.input)(inputMatcher))

    def whenDecodingInputFailure[E, O](endpoint: Endpoint[_, _, E, O, _]): EndpointWhenRequest[_, _, E, O] =
      whenDecodingInputFailureMatches(endpoint) { case _ => true }

    def whenDecodingInputFailureMatches[E, O](
        endpoint: Endpoint[_, _, E, O, _]
    )(failureMatcher: PartialFunction[DecodeResult.Failure, Boolean]): EndpointWhenRequest[_, _, E, O] =
      new EndpointWhenRequest(
        endpoint,
        new stub.WhenRequest(req => {
          val (result, _) = DecodeBasicInputs(endpoint.input, DecodeInputsContext(new SttpRequest(req)))
          result match {
            case DecodeBasicInputsResult.Failure(_, f) if failureMatcher.isDefinedAt(f) => failureMatcher(f)
            case _                                                                      => false
          }
        })
      )

    private def whenInputMatches[A, I, E, O](input: EndpointInput[I])(inputMatcher: I => Boolean): stub.WhenRequest =
      new stub.WhenRequest(req =>
        SttpRequestDecoder(req, input) match {
          case _: DecodeBasicInputsResult.Failure => false
          case values: DecodeBasicInputsResult.Values =>
            InputValue(input, values) match {
              case InputValueResult.Value(params, _) => inputMatcher(params.asAny.asInstanceOf[I])
              case _: InputValueResult.Failure       => false
            }
        }
      )

    class EndpointWhenRequest[A, I, E, O](endpoint: Endpoint[A, I, E, O, _], whenRequest: stub.WhenRequest) {
      def thenSuccess(response: O): STUB =
        whenRequest.thenRespond(adjustBody(SttpResponseEncoder(endpoint.output, response, StatusCode.Ok)))

      def thenError(errorResponse: E, statusCode: StatusCode): STUB =
        whenRequest.thenRespond(adjustBody(SttpResponseEncoder(endpoint.errorOutput, errorResponse, statusCode)))

      private def adjustBody(r: Response[Any]): Response[StubBody] = r.copy(body = StubBody.Adjust(r.body))

      /** Exposes underlying generic stubbing which allows to stub with an arbitrary response */
      def generic: stub.WhenRequest = whenRequest
    }
  }
}
