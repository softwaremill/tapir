package sttp.tapir.server.stub

import sttp.client3.testing._
import sttp.model._
import sttp.tapir.server.interpreter._
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

trait SttpStubServer {

  implicit class RichSttpBackendStub[F[_], R](val stub: SttpBackendStub[F, R]) {
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
          case _: DecodeBasicInputsResult.Failure     => false
          case values: DecodeBasicInputsResult.Values =>
            InputValue(input, values) match {
              case InputValueResult.Value(params, _) => inputMatcher(params.asAny.asInstanceOf[I])
              case _: InputValueResult.Failure       => false
            }
        }
      )

    class EndpointWhenRequest[A, I, E, O](endpoint: Endpoint[A, I, E, O, _], whenRequest: stub.WhenRequest) {
      def thenSuccess(response: O): SttpBackendStub[F, R] =
        whenRequest.thenRespond(SttpResponseEncoder(endpoint.output, response, StatusCode.Ok))

      def thenError(errorResponse: E, statusCode: StatusCode): SttpBackendStub[F, R] =
        whenRequest.thenRespond(SttpResponseEncoder(endpoint.errorOutput, errorResponse, statusCode))

      /** Exposes underlying generic stubbing which allows to stub with an arbitrary response */
      def generic: stub.WhenRequest = whenRequest
    }
  }
}
