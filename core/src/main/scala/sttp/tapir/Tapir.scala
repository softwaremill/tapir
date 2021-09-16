package sttp.tapir

import sttp.capabilities.Streams
import sttp.model.headers.{Cookie, CookieValueWithMeta, CookieWithMeta}
import sttp.model._
import sttp.tapir.CodecFormat.{Json, OctetStream, TextPlain, Xml}
import sttp.tapir.EndpointOutput.OneOfMapping
import sttp.tapir.static.TapirStaticContentEndpoints
import sttp.tapir.internal.{ModifyMacroSupport, _}
import sttp.tapir.macros.TapirMacros
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.MatchType
import sttp.ws.WebSocketFrame

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

trait Tapir extends TapirExtensions with TapirComputedInputs with TapirStaticContentEndpoints with ModifyMacroSupport with TapirMacros {
  implicit def stringToPath(s: String): EndpointInput.FixedPath[Unit] = EndpointInput.FixedPath(s, Codec.idPlain(), EndpointIO.Info.empty)

  def path[T: Codec[String, *, TextPlain]]: EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(None, implicitly, EndpointIO.Info.empty)
  def path[T: Codec[String, *, TextPlain]](name: String): EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(Some(name), implicitly, EndpointIO.Info.empty)
  def paths: EndpointInput.PathsCapture[List[String]] = EndpointInput.PathsCapture(Codec.idPlain(), EndpointIO.Info.empty)

  def query[T: Codec[List[String], *, TextPlain]](name: String): EndpointInput.Query[T] =
    EndpointInput.Query(name, implicitly, EndpointIO.Info.empty)
  def queryParams: EndpointInput.QueryParams[QueryParams] = EndpointInput.QueryParams(Codec.idPlain(), EndpointIO.Info.empty)

  def header[T: Codec[List[String], *, TextPlain]](name: String): EndpointIO.Header[T] =
    EndpointIO.Header(name, implicitly, EndpointIO.Info.empty)
  def header(h: Header): EndpointIO.FixedHeader[Unit] = EndpointIO.FixedHeader(h, Codec.idPlain(), EndpointIO.Info.empty)
  def header(name: String, value: String): EndpointIO.FixedHeader[Unit] = header(sttp.model.Header(name, value))
  def headers: EndpointIO.Headers[List[sttp.model.Header]] = EndpointIO.Headers(Codec.idPlain(), EndpointIO.Info.empty)

  // TODO: cache directives
  def cookie[T: Codec[Option[String], *, TextPlain]](name: String): EndpointInput.Cookie[T] =
    EndpointInput.Cookie(name, implicitly, EndpointIO.Info.empty)
  def cookies: EndpointIO.Header[List[Cookie]] = header[List[Cookie]](HeaderNames.Cookie)
  def setCookie(name: String): EndpointIO.Header[CookieValueWithMeta] = setCookieOpt(name).mapDecode {
    case None    => DecodeResult.Missing
    case Some(v) => DecodeResult.Value(v)
  }(Some(_))
  def setCookieOpt(name: String): EndpointIO.Header[Option[CookieValueWithMeta]] = {
    setCookies.mapDecode(cs =>
      cs.filter(_.name == name) match {
        case Nil     => DecodeResult.Value(None)
        case List(c) => DecodeResult.Value(Some(c.valueWithMeta))
        case l       => DecodeResult.Multiple(l.map(_.toString))
      }
    )(cvo => cvo.map(cv => CookieWithMeta(name, cv)).toList)
  }
  def setCookies: EndpointIO.Header[List[CookieWithMeta]] = header[List[CookieWithMeta]](HeaderNames.SetCookie)

  def stringBody: EndpointIO.Body[String, String] = stringBody(StandardCharsets.UTF_8)
  def stringBody(charset: String): EndpointIO.Body[String, String] = stringBody(Charset.forName(charset))
  def stringBody(charset: Charset): EndpointIO.Body[String, String] =
    EndpointIO.Body(RawBodyType.StringBody(charset), Codec.string, EndpointIO.Info.empty)

  val htmlBodyUtf8: EndpointIO.Body[String, String] =
    EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), Codec.string.format(CodecFormat.TextHtml()), EndpointIO.Info.empty)

  def plainBody[T: Codec[String, *, TextPlain]]: EndpointIO.Body[String, T] = plainBody(StandardCharsets.UTF_8)
  def plainBody[T: Codec[String, *, TextPlain]](charset: Charset): EndpointIO.Body[String, T] =
    EndpointIO.Body(RawBodyType.StringBody(charset), implicitly, EndpointIO.Info.empty)

  @scala.deprecated(message = "Use customJsonBody", since = "0.18.0")
  def anyJsonBody[T: Codec.JsonCodec]: EndpointIO.Body[String, T] = customJsonBody[T]

  /** Requires an implicit [[Codec.JsonCodec]] in scope. Such a codec can be created using [[Codec.json]].
    *
    * However, json codecs are usually derived from json-library-specific implicits. That's why integrations with various json libraries
    * define `jsonBody` methods, which directly require the library-specific implicits.
    *
    * Unless you have defined a custom json codec, the `jsonBody` methods should be used.
    */
  def customJsonBody[T: Codec.JsonCodec]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(implicitly[Codec[String, T, Json]])

  /** Requires an implicit [[Codec.XmlCodec]] in scope. Such a codec can be created using [[Codec.xml]]. */
  def xmlBody[T: Codec.XmlCodec]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(implicitly[Codec[String, T, Xml]])

  def rawBinaryBody[R: RawBodyType.Binary](implicit codec: Codec[R, R, OctetStream]): EndpointIO.Body[R, R] =
    EndpointIO.Body(implicitly[RawBodyType.Binary[R]], codec, EndpointIO.Info.empty)
  def binaryBody[R: RawBodyType.Binary, T: Codec[R, *, OctetStream]]: EndpointIO.Body[R, T] =
    EndpointIO.Body(implicitly[RawBodyType.Binary[R]], implicitly[Codec[R, T, OctetStream]], EndpointIO.Info.empty)

  def byteArrayBody: EndpointIO.Body[Array[Byte], Array[Byte]] = rawBinaryBody[Array[Byte]]
  def byteBufferBody: EndpointIO.Body[ByteBuffer, ByteBuffer] = rawBinaryBody[ByteBuffer]
  def inputStreamBody: EndpointIO.Body[InputStream, InputStream] = rawBinaryBody[InputStream]
  def fileBody: EndpointIO.Body[TapirFile, TapirFile] = rawBinaryBody[TapirFile]

  def formBody[T: Codec[String, *, CodecFormat.XWwwFormUrlencoded]]: EndpointIO.Body[String, T] =
    anyFromUtf8StringBody[T, CodecFormat.XWwwFormUrlencoded](implicitly)
  def formBody[T: Codec[String, *, CodecFormat.XWwwFormUrlencoded]](charset: Charset): EndpointIO.Body[String, T] =
    anyFromStringBody[T, CodecFormat.XWwwFormUrlencoded](implicitly, charset)

  val multipartBody: EndpointIO.Body[Seq[RawPart], Seq[Part[Array[Byte]]]] = multipartBody(MultipartCodec.Default)
  def multipartBody[T](implicit multipartCodec: MultipartCodec[T]): EndpointIO.Body[Seq[RawPart], T] =
    EndpointIO.Body(multipartCodec.rawBodyType, multipartCodec.codec, EndpointIO.Info.empty)

  /** Creates a stream body with a binary schema. The `application/octet-stream` media type will be used by default, but can be later
    * overridden by providing a custom `Content-Type` header value.
    * @param s
    *   A supported streams implementation.
    */
  def streamBinaryBody[S](
      s: Streams[S]
  ): StreamBodyIO[s.BinaryStream, s.BinaryStream, S] =
    StreamBodyIO(s, Codec.id(CodecFormat.OctetStream(), Schema.binary), EndpointIO.Info.empty, None)

  /** Creates a stream body with a text schema.
    * @param s
    *   A supported streams implementation.
    * @param format
    *   The media type to use by default. Can be later overridden by providing a custom `Content-Type` header.
    * @param charset
    *   An optional charset of the resulting stream's data, to be used in the content type.
    */
  def streamTextBody[S](
      s: Streams[S]
  )(format: CodecFormat, charset: Option[Charset] = None): StreamBodyIO[s.BinaryStream, s.BinaryStream, S] =
    StreamBodyIO(s, Codec.id(format, Schema.string), EndpointIO.Info.empty, charset)

  /** Creates a stream body with a text schema.
    * @param s
    *   A supported streams implementation.
    * @param schema
    *   Schema of the body. This should be a schema for the "deserialized" stream.
    * @param format
    *   The media type to use by default. Can be later overridden by providing a custom `Content-Type` header.
    * @param charset
    *   An optional charset of the resulting stream's data, to be used in the content type.
    */
  def streamBody[S, T](
      s: Streams[S]
  )(schema: Schema[T], format: CodecFormat, charset: Option[Charset] = None): StreamBodyIO[s.BinaryStream, s.BinaryStream, S] =
    StreamBodyIO(s, Codec.id(format, schema.as[s.BinaryStream]), EndpointIO.Info.empty, charset)

  // the intermediate class is needed so that only two type parameters need to be given to webSocketBody[A, B],
  // while the third one (S) can be inferred.
  final class WebSocketBodyBuilder[REQ, REQ_CF <: CodecFormat, RESP, RESP_CF <: CodecFormat] {
    def apply[S](
        s: Streams[S]
    )(implicit
        requests: Codec[WebSocketFrame, REQ, REQ_CF],
        responses: Codec[WebSocketFrame, RESP, RESP_CF]
    ): WebSocketBodyOutput[s.Pipe[REQ, RESP], REQ, RESP, s.Pipe[REQ, RESP], S] =
      WebSocketBodyOutput(
        s,
        requests,
        responses,
        Codec.idPlain(), // any codec format will do
        EndpointIO.Info.empty,
        EndpointIO.Info.empty,
        EndpointIO.Info.empty,
        concatenateFragmentedFrames = true,
        ignorePong = true,
        autoPongOnPing = true,
        decodeCloseRequests = requests.schema.isOptional,
        decodeCloseResponses = responses.schema.isOptional,
        autoPing = Some((13.seconds, WebSocketFrame.ping))
      )
  }

  /** @tparam REQ
    *   The type of messages that are sent to the server.
    * @tparam REQ_CF
    *   The codec format (media type) of messages that are sent to the server.
    * @tparam RESP
    *   The type of messages that are received from the server.
    * @tparam RESP_CF
    *   The codec format (media type) of messages that are received from the server.
    */
  def webSocketBody[REQ, REQ_CF <: CodecFormat, RESP, RESP_CF <: CodecFormat]: WebSocketBodyBuilder[REQ, REQ_CF, RESP, RESP_CF] =
    new WebSocketBodyBuilder[REQ, REQ_CF, RESP, RESP_CF]
  def webSocketBodyRaw[S](
      s: Streams[S]
  ): WebSocketBodyOutput[s.Pipe[WebSocketFrame, WebSocketFrame], WebSocketFrame, WebSocketFrame, s.Pipe[
    WebSocketFrame,
    WebSocketFrame
  ], S] =
    new WebSocketBodyBuilder[WebSocketFrame, CodecFormat, WebSocketFrame, CodecFormat]
      .apply(s)
      .concatenateFragmentedFrames(false)
      .ignorePong(false)
      .autoPongOnPing(false)
      .decodeCloseRequests(true)
      .decodeCloseResponses(true)
      .autoPing(None)

  /** A body in any format, read using the given `codec`, from a raw string read using UTF-8.
    */
  def anyFromUtf8StringBody[T, CF <: CodecFormat](codec: Codec[String, T, CF]): EndpointIO.Body[String, T] =
    anyFromStringBody[T, CF](codec, StandardCharsets.UTF_8)

  /** A body in any format, read using the given `codec`, from a raw string read using `charset`.
    */
  def anyFromStringBody[T, CF <: CodecFormat](codec: Codec[String, T, CF], charset: Charset): EndpointIO.Body[String, T] =
    EndpointIO.Body(RawBodyType.StringBody(charset), codec, EndpointIO.Info.empty)

  def auth: TapirAuth.type = TapirAuth

  /** Extract a value from a server request. This input is only used by server interpreters, it is ignored by documentation interpreters and
    * the provided value is discarded by client interpreters.
    */
  def extractFromRequest[T](f: ServerRequest => T): EndpointInput.ExtractFromRequest[T] =
    EndpointInput.ExtractFromRequest(Codec.idPlain[ServerRequest]().map(f)(_ => null), EndpointIO.Info.empty)

  def statusCode: EndpointOutput.StatusCode[sttp.model.StatusCode] =
    EndpointOutput.StatusCode(Map.empty, Codec.idPlain(), EndpointIO.Info.empty)
  def statusCode(statusCode: sttp.model.StatusCode): EndpointOutput.FixedStatusCode[Unit] =
    EndpointOutput.FixedStatusCode(statusCode, Codec.idPlain(), EndpointIO.Info.empty)

  /** Specifies a correspondence between status codes and outputs.
    *
    * All outputs must have a common supertype (`T`). Typically, the supertype is a sealed trait, and the mappings are implementing cases
    * classes.
    *
    * A single status code can have multiple mappings (or there can be multiple default mappings), with different body content types. The
    * mapping can then be chosen based on content type negotiation, or the content type header.
    *
    * Note that exhaustiveness of the mappings is not checked (that all subtypes of `T` are covered).
    */
  def oneOf[T](firstCase: OneOfMapping[_ <: T], otherCases: OneOfMapping[_ <: T]*): EndpointOutput.OneOf[T, T] =
    EndpointOutput.OneOf[T, T](firstCase +: otherCases.toList, Mapping.id)

  /** Create a one-of-mapping which uses `statusCode` and `output` if the class of the provided value (when interpreting as a server)
    * matches the given `runtimeClass`. Note that this does not take into account type erasure.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfMappingClassMatcher[T](
      statusCode: StatusCode,
      output: EndpointOutput[T],
      runtimeClass: Class[_]
  ): OneOfMapping[T] = {
    OneOfMapping(Some(statusCode), output, { (a: Any) => runtimeClass.isInstance(a) })
  }

  @scala.deprecated("Use oneOfMappingClassMatcher", since = "0.18")
  def statusMappingClassMatcher[T](
      statusCode: StatusCode,
      output: EndpointOutput[T],
      runtimeClass: Class[_]
  ): OneOfMapping[T] = oneOfMappingClassMatcher(statusCode, output, runtimeClass)

  /** Create a one-of-mapping which uses `statusCode` and `output` if the provided value (when interpreting as a server matches the
    * `matcher` predicate.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfMappingValueMatcher[T](statusCode: StatusCode, output: EndpointOutput[T])(
      matcher: PartialFunction[Any, Boolean]
  ): OneOfMapping[T] =
    OneOfMapping(Some(statusCode), output, matcher.lift.andThen(_.getOrElse(false)))

  @scala.deprecated("Use oneOfMappingValueMatcher", since = "0.18")
  def statusMappingValueMatcher[T](statusCode: StatusCode, output: EndpointOutput[T])(
      matcher: PartialFunction[Any, Boolean]
  ): OneOfMapping[T] = oneOfMappingValueMatcher(statusCode, output)(matcher)

  /** Create a one-of-mapping which uses `statusCode` and `output` if the provided value exactly matches one of the values provided in the
    * second argument list.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfMappingExactMatcher[T: ClassTag](
      statusCode: StatusCode,
      output: EndpointOutput[T]
  )(
      firstExactValue: T,
      rest: T*
  ): OneOfMapping[T] =
    oneOfMappingValueMatcher(statusCode, output)(exactMatch(rest.toSet + firstExactValue))

  @scala.deprecated("Use oneOfMappingExactMatcher", since = "0.18")
  def statusMappingExactMatcher[T: ClassTag](
      statusCode: StatusCode,
      output: EndpointOutput[T]
  )(
      firstExactValue: T,
      rest: T*
  ): OneOfMapping[T] = oneOfMappingExactMatcher(statusCode, output)(firstExactValue, rest: _*)

  /** Experimental!
    *
    * Create a one-of-mapping which uses `statusCode` and `output` if the provided value matches the target type, as checked by
    * [[MatchType]]. Instances of [[MatchType]] are automatically derived and recursively check that classes of all fields match, to bypass
    * issues caused by type erasure.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfMappingFromMatchType[T: MatchType](statusCode: StatusCode, output: EndpointOutput[T]): OneOfMapping[T] =
    oneOfMappingValueMatcher(statusCode, output)(implicitly[MatchType[T]].partial)

  @scala.deprecated("Use oneOfMappingFromMatchType", since = "0.18")
  def statusMappingFromMatchType[T: MatchType](statusCode: StatusCode, output: EndpointOutput[T]): OneOfMapping[T] =
    oneOfMappingValueMatcher(statusCode, output)(implicitly[MatchType[T]].partial)

  /** Create a fallback mapping to be used in [[oneOf]] output descriptions. Multiple such mappings can be specified, with different body
    * content types.
    */
  def oneOfDefaultMapping[T](output: EndpointOutput[T]): OneOfMapping[T] = {
    OneOfMapping(None, output, _ => true)
  }

  @scala.deprecated("Use oneOfDefaultMapping", since = "0.18")
  def statusDefaultMapping[T](output: EndpointOutput[T]): OneOfMapping[T] = oneOfDefaultMapping(output)

  /** An empty output. Useful if one of `oneOf` branches should be mapped to the status code only.
    */
  val emptyOutput: EndpointIO.Empty[Unit] = EndpointIO.Empty(Codec.idPlain(), EndpointIO.Info.empty)

  /** An empty output. Useful if one of the [[oneOf]] branches of a coproduct type is a case object that should be mapped to an empty body.
    */
  def emptyOutputAs[T](value: T): EndpointOutput.Basic[T] = emptyOutput.map(_ => value)(_ => ())

  private[tapir] val emptyInput: EndpointInput[Unit] = EndpointIO.Empty(Codec.idPlain(), EndpointIO.Info.empty)

  val infallibleEndpoint: Endpoint[Unit, Nothing, Unit, Any] =
    Endpoint[Unit, Nothing, Unit, Any](
      emptyInput,
      EndpointOutput.Void(),
      emptyOutput,
      EndpointInfo(None, None, None, Vector.empty, deprecated = false, Vector.empty)
    )

  val endpoint: Endpoint[Unit, Unit, Unit, Any] = infallibleEndpoint.copy(errorOutput = emptyOutput)
}

trait TapirComputedInputs { this: Tapir =>
  def clientIp: EndpointInput[Option[String]] =
    extractFromRequest(request =>
      request
        .header(HeaderNames.XForwardedFor)
        .flatMap(_.split(",").headOption)
        .orElse(request.header("Remote-Address"))
        .orElse(request.header("X-Real-Ip"))
        .orElse(request.connectionInfo.remote.flatMap(a => Option(a.getAddress.getHostAddress)))
    )

  def isWebSocket: EndpointInput[Boolean] =
    extractFromRequest(request =>
      (for {
        connection <- request.header(HeaderNames.Connection)
        upgrade <- request.header(HeaderNames.Upgrade)
      } yield connection.equalsIgnoreCase("Upgrade") && upgrade.equalsIgnoreCase("websocket")).getOrElse(false)
    )
}
