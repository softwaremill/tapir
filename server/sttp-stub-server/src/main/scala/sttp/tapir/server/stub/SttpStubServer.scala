package sttp.tapir.server.stub

import java.nio.charset.Charset

import sttp.client3.RequestMetadata
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.server.internal.{
  DecodeInputs,
  DecodeInputsResult,
  EncodeOutputBody,
  EncodeOutputs,
  InputValues,
  InputValuesResult,
  OutputValues
}
import sttp.tapir.{CodecFormat, DecodeResult, Endpoint, EndpointOutput, RawBodyType}

trait SttpStubServer {

  implicit class RichSttpBackendStub[F[_], R](val stub: SttpBackendStub[F, R]) {
    def whenRequestMatchesEndpoint[E, O](endpoint: Endpoint[_, E, O, _]): TypeAwareWhenRequest[_, E, O] = {
      new TypeAwareWhenRequest(
        endpoint,
        new stub.WhenRequest(req =>
          DecodeInputs(endpoint.input, new SttpDecodeInputs(req)) match {
            case DecodeInputsResult.Failure(_, _) => false
            case DecodeInputsResult.Values(_, _)  => true
          }
        )
      )
    }

    def whenInputMatches[I, E, O](endpoint: Endpoint[I, E, O, _])(inputMatcher: I => Boolean): TypeAwareWhenRequest[I, E, O] = {
      new TypeAwareWhenRequest(
        endpoint,
        new stub.WhenRequest(req =>
          DecodeInputs(endpoint.input, new SttpDecodeInputs(req)) match {
            case DecodeInputsResult.Failure(_, _) => false
            case values: DecodeInputsResult.Values =>
              InputValues(endpoint.input, values) match {
                case InputValuesResult.Value(params, _) => inputMatcher(params.asAny.asInstanceOf[I])
                case InputValuesResult.Failure(_, _)    => false
              }
          }
        )
      )
    }

    def whenDecodingInputFailureMatches[E, O](
        endpoint: Endpoint[_, E, O, _]
    )(failureMatcher: PartialFunction[DecodeResult.Failure, Boolean]): TypeAwareWhenRequest[_, E, O] = {
      new TypeAwareWhenRequest(
        endpoint,
        new stub.WhenRequest(req => {
          val result = DecodeInputs(endpoint.input, new SttpDecodeInputs(req))
          result match {
            case DecodeInputsResult.Failure(_, f) if failureMatcher.isDefinedAt(f) => failureMatcher(f)
            case DecodeInputsResult.Values(_, _)                                   => false
          }
        })
      )
    }

    def whenDecodingInputFailure[E, O](endpoint: Endpoint[_, E, O, _]): TypeAwareWhenRequest[_, E, O] = {
      whenDecodingInputFailureMatches(endpoint) { case _ => true }
    }

    class TypeAwareWhenRequest[I, E, O](endpoint: Endpoint[I, E, O, _], whenRequest: stub.WhenRequest) {

      def thenSuccess(response: O): SttpBackendStub[F, R] =
        thenRespondWithOutput(endpoint.output, response, StatusCode.Ok)

      def thenError(errorResponse: E, statusCode: StatusCode): SttpBackendStub[F, R] =
        thenRespondWithOutput(endpoint.errorOutput, errorResponse, statusCode)

      private def thenRespondWithOutput(
          output: EndpointOutput[_],
          responseValue: Any,
          statusCode: StatusCode
      ): SttpBackendStub[F, R] = {
        val encodeOutputBody = new EncodeOutputBody[Any] {
          override def rawValueToBody(v: Any, format: CodecFormat, bodyType: RawBodyType[_]): Any = v
          override def streamValueToBody(v: Any, format: CodecFormat, charset: Option[Charset]): Any = v
        }
        val outputValues = new EncodeOutputs[Any](encodeOutputBody).apply(output, ParamsAsAny(responseValue), OutputValues.empty)
        whenRequest.thenRespond(
          sttp.client3.Response(
            outputValues.body.getOrElse(()),
            outputValues.statusCode.getOrElse(statusCode),
            "",
            outputValues.headers.map { case (k, v) => sttp.model.Header.unsafeApply(k, v) },
            Nil,
            RequestMetadata.ExampleGet
          )
        )
      }

      /**
        * Exposes underlying generic stubbing which allows to stub with an arbitrary response
        */
      def generic: stub.WhenRequest = whenRequest
    }
  }
}
