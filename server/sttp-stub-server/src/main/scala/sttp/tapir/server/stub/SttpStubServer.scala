package sttp.tapir.server.stub

import sttp.client3.testing._
import sttp.client3.{Identity, Request, Response}
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir.internal.{NoStreams, ParamsAsAny}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{Interceptor, RequestResult}
import sttp.tapir.server.interpreter._
import sttp.tapir.server.stub.SttpStubServer.{requestBody, toResponseBody}
import sttp.tapir.{CodecFormat, DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput, RawBodyType, WebSocketBodyOutput}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.collection.immutable.Seq
import scala.util.{Success, Try}

trait SttpStubServer {

  implicit class RichSttpBackendStub[F[_], R](val stub: SttpBackendStub[F, R]) {
    def whenRequestMatchesEndpoint[E, O](endpoint: Endpoint[_, _, E, O, _]): TypeAwareWhenRequest[_, _, E, O] =
      new TypeAwareWhenRequest(endpoint, _whenRequestMatches(endpoint))

    def whenSecurityInputMatches[A, I, E, O](endpoint: Endpoint[A, I, E, O, _])(
        securityInputMatcher: A => Boolean
    ): TypeAwareWhenRequest[A, I, E, O] =
      new TypeAwareWhenRequest(endpoint, _whenInputMatches(endpoint.securityInput)(securityInputMatcher))

    def whenInputMatches[A, I, E, O](endpoint: Endpoint[A, I, E, O, _])(inputMatcher: I => Boolean): TypeAwareWhenRequest[A, I, E, O] =
      new TypeAwareWhenRequest(endpoint, _whenInputMatches(endpoint.input)(inputMatcher))

    def whenRequestMatchesEndpointThenLogic(
        endpoint: ServerEndpoint[R, F],
        interceptors: List[Interceptor[F]] = Nil
    ): SttpBackendStub[F, R] =
      _whenRequestMatches(endpoint.endpoint).thenRespondF(req => interpretRequest(req, endpoint, interceptors))

    def whenSecurityInputMatchesThenLogic[A](
        endpoint: ServerEndpoint.Full[A, _, _, _, _, R, F],
        interceptors: List[Interceptor[F]] = Nil
    )(
        securityInputMatcher: A => Boolean
    ): SttpBackendStub[F, R] =
      _whenInputMatches(endpoint.endpoint.securityInput)(securityInputMatcher).thenRespondF(req =>
        interpretRequest(req, endpoint, interceptors)
      )

    def whenInputMatchesThenLogic[I](
        endpoint: ServerEndpoint.Full[_, _, I, _, _, R, F],
        interceptors: List[Interceptor[F]] = Nil
    )(
        inputMatcher: I => Boolean
    ): SttpBackendStub[F, R] =
      _whenInputMatches(endpoint.endpoint.input)(inputMatcher).thenRespondF(req => interpretRequest(req, endpoint, interceptors))

    private def _whenRequestMatches[E, O](endpoint: Endpoint[_, _, E, O, _]): stub.WhenRequest = {
      new stub.WhenRequest(req =>
        DecodeBasicInputs(endpoint.input, DecodeInputsContext(new SttpRequest(req))) match {
          case (_: DecodeBasicInputsResult.Failure, _) => false
          case (_: DecodeBasicInputsResult.Values, _)  => true
        }
      )
    }

    private def _whenInputMatches[A, I, E, O](input: EndpointInput[I])(inputMatcher: I => Boolean): stub.WhenRequest = {
      new stub.WhenRequest(req =>
        decodeBody(req, DecodeBasicInputs(input, DecodeInputsContext(new SttpRequest(req)))._1) match {
          case _: DecodeBasicInputsResult.Failure => false
          case values: DecodeBasicInputsResult.Values =>
            InputValue(input, values) match {
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

    private def interpretRequest(
        req: Request[_, _],
        endpoint: ServerEndpoint[R, F],
        interceptors: List[Interceptor[F]]
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

      implicit val me = stub.responseMonad

      val interpreter =
        new ServerInterpreter[R, F, Any, NoStreams](
          requestBody[F](req.forceBodyAsByteArray),
          toResponseBody,
          interceptors,
          _ => stub.responseMonad.unit(())
        )(
          stub.responseMonad,
          new BodyListener[F, Any] {
            override def onComplete(body: Any)(cb: Try[Unit] => F[Unit]): F[Any] = stub.responseMonad.map(cb(Success(())))(_ => body)
          }
        )
      val sRequest = new SttpRequest(req)
      stub.responseMonad.map(interpreter.apply(sRequest, endpoint)) {
        case RequestResult.Response(sResponse) => toResponse(sRequest, sResponse)
        case RequestResult.Failure(_)          => toResponse(sRequest, ServerResponse(StatusCode.NotFound, Nil, None))
      }
    }

    def whenDecodingInputFailureMatches[E, O](
        endpoint: Endpoint[_, _, E, O, _]
    )(failureMatcher: PartialFunction[DecodeResult.Failure, Boolean]): TypeAwareWhenRequest[_, _, E, O] = {
      new TypeAwareWhenRequest(
        endpoint,
        new stub.WhenRequest(req => {
          val (result, _) = DecodeBasicInputs(endpoint.input, DecodeInputsContext(new SttpRequest(req)))
          result match {
            case DecodeBasicInputsResult.Failure(_, f) if failureMatcher.isDefinedAt(f) => failureMatcher(f)
            case _                                                                      => false
          }
        })
      )
    }

    def whenDecodingInputFailure[E, O](endpoint: Endpoint[_, _, E, O, _]): TypeAwareWhenRequest[_, _, E, O] = {
      whenDecodingInputFailureMatches(endpoint) { case _ => true }
    }

    class TypeAwareWhenRequest[A, I, E, O](endpoint: Endpoint[A, I, E, O, _], whenRequest: stub.WhenRequest) {

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
          new EncodeOutputs[Any, NoStreams](toResponseBody, Seq(ContentTypeRange.AnyRange))
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
  private val toResponseBody: ToResponseBody[Any, NoStreams] = new ToResponseBody[Any, NoStreams] {
    override val streams: NoStreams = NoStreams
    override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
    ): Any = pipe // impossible
  }

  private def requestBody[F[_]](bytes: Array[Byte])(implicit F: MonadError[F]): RequestBody[F, NoStreams] = new RequestBody[F, NoStreams] {
    override val streams: NoStreams = NoStreams
    override def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]] =
      bodyType match {
        case RawBodyType.StringBody(charset) => F.unit(RawValue(new String(bytes, charset)))
        case RawBodyType.ByteArrayBody       => F.unit(RawValue(bytes))
        case RawBodyType.ByteBufferBody      => F.unit(RawValue(ByteBuffer.wrap(bytes)))
        case RawBodyType.InputStreamBody     => F.unit(RawValue(new ByteArrayInputStream(bytes)))
        case RawBodyType.FileBody            => F.error(new UnsupportedOperationException)
        case _: RawBodyType.MultipartBody    => F.error(new UnsupportedOperationException)
      }
    override def toStream(): streams.BinaryStream = ???
  }
}
