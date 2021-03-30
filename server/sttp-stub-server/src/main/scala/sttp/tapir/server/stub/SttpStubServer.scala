package sttp.tapir.server.stub

import sttp.client3.testing._
import sttp.client3.{Identity, Request, Response}
import sttp.model._
import sttp.tapir.internal.{NoStreams, ParamsAsAny}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.interpreter._
import sttp.tapir.{CodecFormat, DecodeResult, Endpoint, EndpointIO, EndpointOutput, RawBodyType, WebSocketBodyOutput}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.collection.immutable.Seq

trait SttpStubServer {

  implicit class RichSttpBackendStub[F[_], R](val stub: SttpBackendStub[F, R]) {
    def whenRequestMatchesEndpoint[E, O](endpoint: Endpoint[_, E, O, _]): TypeAwareWhenRequest[_, E, O] = {
      new TypeAwareWhenRequest(
        endpoint,
        new stub.WhenRequest(req =>
          DecodeBasicInputs(endpoint.input, new SttpRequest(req)) match {
            case _: DecodeBasicInputsResult.Failure => false
            case _: DecodeBasicInputsResult.Values  => true
          }
        )
      )
    }

    def whenInputMatches[I, E, O](endpoint: Endpoint[I, E, O, _])(inputMatcher: I => Boolean): TypeAwareWhenRequest[I, E, O] = {
      new TypeAwareWhenRequest(
        endpoint,
        new stub.WhenRequest(req =>
          decodeBody(req, DecodeBasicInputs(endpoint.input, new SttpRequest(req))) match {
            case _: DecodeBasicInputsResult.Failure => false
            case values: DecodeBasicInputsResult.Values =>
              InputValue(endpoint.input, values) match {
                case InputValueResult.Value(params, _) => inputMatcher(params.asAny.asInstanceOf[I])
                case _: InputValueResult.Failure       => false
              }
          }
        )
      )
    }

    private def decodeBody(request: Request[_, _], result: DecodeBasicInputsResult): DecodeBasicInputsResult = result match {
      case values: DecodeBasicInputsResult.Values =>
        values.bodyInputWithIndex match {
          case Some((Left(bodyInput @ EndpointIO.Body(_, codec, _)), _)) =>
            codec.decode(rawBody(request, bodyInput)) match {
              case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
              case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
            }
          case Some((Right(_), _)) => throw new UnsupportedOperationException // streaming is not supported
          case None                => values
        }
      case failure: DecodeBasicInputsResult.Failure => failure
    }

    private def rawBody[RAW](request: Request[_, _], body: EndpointIO.Body[RAW, _]): Identity[RAW] = {
      val asByteArray = request.forceBodyAsByteArray
      body.bodyType match {
        case RawBodyType.StringBody(charset) => new String(asByteArray, charset)
        case RawBodyType.ByteArrayBody       => asByteArray
        case RawBodyType.ByteBufferBody      => ByteBuffer.wrap(asByteArray)
        case RawBodyType.InputStreamBody     => new ByteArrayInputStream(asByteArray)
        case RawBodyType.FileBody            => throw new UnsupportedOperationException
        case _: RawBodyType.MultipartBody    => throw new UnsupportedOperationException
      }
    }

    def whenDecodingInputFailureMatches[E, O](
        endpoint: Endpoint[_, E, O, _]
    )(failureMatcher: PartialFunction[DecodeResult.Failure, Boolean]): TypeAwareWhenRequest[_, E, O] = {
      new TypeAwareWhenRequest(
        endpoint,
        new stub.WhenRequest(req => {
          val result = DecodeBasicInputs(endpoint.input, new SttpRequest(req))
          result match {
            case DecodeBasicInputsResult.Failure(_, f) if failureMatcher.isDefinedAt(f) => failureMatcher(f)
            case _                                                                      => false
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
        val toResponseBody: ToResponseBody[Any, Nothing] = new ToResponseBody[Any, Nothing] {
          override val streams: NoStreams = NoStreams
          override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
          override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
          override def fromWebSocketPipe[REQ, RESP](
              pipe: streams.Pipe[REQ, RESP],
              o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
          ): Any = pipe // impossible
        }

        val outputValues: OutputValues[Any] =
          new EncodeOutputs[Any, Nothing](toResponseBody, Right(Seq(ContentTypeRange.AnyRange)))
            .apply(output, ParamsAsAny(responseValue), OutputValues.empty)

        whenRequest.thenRespond(
          sttp.client3.Response(
            outputValues.body.map(_.apply(Headers(outputValues.headers))).getOrElse(()),
            outputValues.statusCode.getOrElse(statusCode),
            "",
            outputValues.headers,
            Nil,
            Response.ExampleGet
          )
        )
      }

      /** Exposes underlying generic stubbing which allows to stub with an arbitrary response
        */
      def generic: stub.WhenRequest = whenRequest
    }
  }
}
