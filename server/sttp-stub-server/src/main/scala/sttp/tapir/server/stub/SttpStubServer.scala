package sttp.tapir.server.stub

import sttp.capabilities.Streams
import sttp.client3.testing._
import sttp.client3.{Identity, Request, Response}
import sttp.model._
import sttp.tapir.internal.{NoStreams, ParamsAsAny}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interpreter.{RequestBody, _}
import sttp.tapir.server.stub.SttpStubServer.{requestBody, toResponseBody}
import sttp.tapir.{CodecFormat, DecodeResult, Endpoint, EndpointIO, EndpointOutput, RawBodyType, WebSocketBodyOutput}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.collection.immutable.Seq

trait SttpStubServer {

  implicit class RichSttpBackendStub[F[_], R](val stub: SttpBackendStub[F, R]) {
    def whenRequestMatchesEndpoint[E, O](endpoint: Endpoint[_, E, O, _]): TypeAwareWhenRequest[_, E, O] =
      new TypeAwareWhenRequest(endpoint, _whenRequestMatches(endpoint))

    def whenInputMatches[I, E, O](endpoint: Endpoint[I, E, O, _])(inputMatcher: I => Boolean): TypeAwareWhenRequest[I, E, O] =
      new TypeAwareWhenRequest(endpoint, _whenInputMatches(endpoint)(inputMatcher))

    def whenRequestMatchesEndpointThenLogic[I, E, O](
        endpoint: ServerEndpoint[I, E, O, R, F],
        interceptors: List[Interceptor[F, Any]] = Nil
    ): SttpBackendStub[F, R] =
      _whenRequestMatches(endpoint.endpoint).thenRespondF(req => interpretRequest(req, endpoint, interceptors))

    def whenInputMatchesThenLogic[I, E, O](endpoint: ServerEndpoint[I, E, O, R, F], interceptors: List[Interceptor[F, Any]] = Nil)(
        inputMatcher: I => Boolean
    ): SttpBackendStub[F, R] =
      _whenInputMatches(endpoint.endpoint)(inputMatcher).thenRespondF(req => interpretRequest(req, endpoint, interceptors))

    private def _whenRequestMatches[E, O](endpoint: Endpoint[_, E, O, _]): stub.WhenRequest = {
      new stub.WhenRequest(req =>
        DecodeBasicInputs(endpoint.input, new SttpRequest(req)) match {
          case _: DecodeBasicInputsResult.Failure => false
          case _: DecodeBasicInputsResult.Values  => true
        }
      )
    }

    private def _whenInputMatches[I, E, O](endpoint: Endpoint[I, E, O, _])(inputMatcher: I => Boolean): stub.WhenRequest = {
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

    private def interpretRequest[I, E, O](
        req: Request[_, _],
        endpoint: ServerEndpoint[I, E, O, R, F],
        interceptors: List[Interceptor[F, Any]]
    ): F[Response[_]] = {
      def toResponse(sRequest: ServerRequest, sResponse: ServerResponse[Any]): Response[Any] = {
        sttp.client3.Response(
          sResponse.body.getOrElse(()),
          sResponse.code,
          "",
          sResponse.headers,
          Nil,
          RequestMetadata(sRequest.method, sRequest.uri, sRequest.headers)
        )
      }

      val interpreter =
        new ServerInterpreter[R, F, Any, Nothing](requestBody[F](), toResponseBody, interceptors, _ => stub.responseMonad.unit(()))(
          stub.responseMonad
        )
      val sRequest = new SttpRequest(req)
      stub.responseMonad.map(interpreter.apply(sRequest, endpoint)) {
        case Some(sResponse) => toResponse(sRequest, sResponse)
        case None            => toResponse(sRequest, ServerResponse(StatusCode.BadRequest, Nil, None))
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
        val outputValues: OutputValues[Any] =
          new EncodeOutputs[Any, Nothing](toResponseBody, Seq(ContentTypeRange.AnyRange))
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

object SttpStubServer {
  private val toResponseBody: ToResponseBody[Any, Nothing] = new ToResponseBody[Any, Nothing] {
    override val streams: NoStreams = NoStreams
    override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
    ): Any = pipe // impossible
  }

  private def requestBody[F[_]](): RequestBody[F, Nothing] = new RequestBody[F, Nothing] {
    override val streams: Streams[Nothing] = NoStreams
    override def toRaw[R](bodyType: RawBodyType[R]): F[R] = ???
    override def toStream(): streams.BinaryStream = ???
  }
}
