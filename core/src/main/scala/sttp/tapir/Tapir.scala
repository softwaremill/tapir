package sttp.tapir

import sttp.capabilities.Streams
import sttp.model._
import sttp.model.headers.{Cookie, CookieValueWithMeta, CookieWithMeta}
import sttp.tapir.CodecFormat.{Json, OctetStream, TextPlain, Xml}
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.internal.{ModifyMacroSupport, _}
import sttp.tapir.model.ServerRequest
import sttp.tapir.static.TapirStaticContentEndpoints
import sttp.tapir.typelevel.{ErasureSameAsType, MatchType}
import sttp.ws.WebSocketFrame

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

trait Tapir extends TapirExtensions with TapirComputedInputs with TapirStaticContentEndpoints with ModifyMacroSupport {
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
  def fileRangeBody: EndpointIO.Body[FileRange, FileRange] = rawBinaryBody[FileRange]
  def fileBody: EndpointIO.Body[FileRange, TapirFile] = rawBinaryBody[FileRange].map(_.file)(d => FileRange(d))

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

  /** A body in any format, read using the given `codec`, from a raw string read using UTF-8. */
  def anyFromUtf8StringBody[T, CF <: CodecFormat](codec: Codec[String, T, CF]): EndpointIO.Body[String, T] =
    anyFromStringBody[T, CF](codec, StandardCharsets.UTF_8)

  /** A body in any format, read using the given `codec`, from a raw string read using `charset`. */
  def anyFromStringBody[T, CF <: CodecFormat](codec: Codec[String, T, CF], charset: Charset): EndpointIO.Body[String, T] =
    EndpointIO.Body(RawBodyType.StringBody(charset), codec, EndpointIO.Info.empty)

  /** Inputs which describe authentication credentials with metadata. */
  def auth: TapirAuth.type = TapirAuth

  /** Extract a value from a server request. This input is only used by server interpreters, it is ignored by documentation interpreters and
    * the provided value is discarded by client interpreters.
    */
  def extractFromRequest[T](f: ServerRequest => T): EndpointInput.ExtractFromRequest[T] =
    EndpointInput.ExtractFromRequest(Codec.idPlain[ServerRequest]().map(f)(_ => null), EndpointIO.Info.empty)

  /** An output which maps to the status code in the response. */
  def statusCode: EndpointOutput.StatusCode[sttp.model.StatusCode] =
    EndpointOutput.StatusCode(Map.empty, Codec.idPlain(), EndpointIO.Info.empty)

  /** An fixed status code output. */
  def statusCode(statusCode: sttp.model.StatusCode): EndpointOutput.FixedStatusCode[Unit] =
    EndpointOutput.FixedStatusCode(statusCode, Codec.idPlain(), EndpointIO.Info.empty)

  /** An output which contains a number of variant outputs.
    *
    * All possible outputs must have a common supertype (`T`). Typically, the supertype is a sealed trait, and the variants are implementing
    * case classes.
    *
    * When encoding to a response, the first matching output is chosen, using the following rules:
    *   1. the variant's `appliesTo` method, applied to the output value (as returned by the server logic) must return `true`.
    *   1. when a fixed content type is specified by the output, it must match the request's `Accept` header (if present). This implements
    *      content negotiation.
    *
    * When decoding from a response, the first output which decodes successfully is chosen.
    *
    * The outputs might vary in status codes, headers (e.g. different content types), and body implementations. However, for bodies, only
    * replayable ones can be used, and they need to have the same raw representation (e.g. all byte-array-base, or all file-based).
    *
    * Note that exhaustiveness of the variants (that all subtypes of `T` are covered) is not checked.
    */
  def oneOf[T](firstVariant: OneOfVariant[_ <: T], otherVariants: OneOfVariant[_ <: T]*): EndpointOutput.OneOf[T, T] =
    EndpointOutput.OneOf[T, T](firstVariant +: otherVariants.toList, Mapping.id)

  /** Create a one-of-variant which uses `output` if the class of the provided value (when interpreting as a server) matches the runtime
    * class of `T`.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this situation would give
    * invalid results. In such cases, use [[oneOfVariantClassMatcher]], [[oneOfVariantValueMatcher]] or [[oneOfVariantFromMatchType]]
    * instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariant[T: ClassTag: ErasureSameAsType](output: EndpointOutput[T]): OneOfVariant[T] =
    oneOfVariantClassMatcher(output, implicitly[ClassTag[T]].runtimeClass)

  /** Create a one-of-variant which uses `output` if the class of the provided value (when interpreting as a server) matches the runtime
    * class of `T`. Adds a fixed status-code output with the given value.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this situation would give
    * invalid results. In such cases, use [[oneOfVariantClassMatcher]], [[oneOfVariantValueMatcher]] or [[oneOfVariantFromMatchType]]
    * instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariant[T: ClassTag: ErasureSameAsType](code: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    oneOfVariant(statusCode(code).and(output))

  @deprecated("Use oneOfVariant", since = "0.19.0")
  def oneOfMapping[T: ClassTag: ErasureSameAsType](code: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    oneOfVariant(code, output)

  /** Create a one-of-variant which uses `output` if the class of the provided value (when interpreting as a server) matches the given
    * `runtimeClass`. Note that this does not take into account type erasure.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantClassMatcher[T](
      output: EndpointOutput[T],
      runtimeClass: Class[_]
  ): OneOfVariant[T] = OneOfVariant(output, { (a: Any) => runtimeClass.isInstance(a) })

  /** Create a one-of-variant which uses `output` i the class of the provided value (when interpreting as a server) matches the given
    * `runtimeClass`. Note that this does not take into account type erasure. Adds a fixed status-code output with the given value.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantClassMatcher[T](
      code: StatusCode,
      output: EndpointOutput[T],
      runtimeClass: Class[_]
  ): OneOfVariant[T] = oneOfVariantClassMatcher(statusCode(code).and(output), runtimeClass)

  @deprecated("Use oneOfVariantClassMatcher", since = "0.19.0")
  def oneOfMappingClassMatcher[T](
      code: StatusCode,
      output: EndpointOutput[T],
      runtimeClass: Class[_]
  ): OneOfVariant[T] = oneOfVariantClassMatcher(code, output, runtimeClass)

  /** Create a one-of-variant which uses `output` if the provided value (when interpreting as a server matches the `matcher` predicate.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantValueMatcher[T](output: EndpointOutput[T])(
      matcher: PartialFunction[Any, Boolean]
  ): OneOfVariant[T] =
    OneOfVariant(output, matcher.lift.andThen(_.getOrElse(false)))

  /** Create a one-of-variant which uses `output` if the provided value (when interpreting as a server matches the `matcher` predicate. Adds
    * a fixed status-code output with the given value.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantValueMatcher[T](code: StatusCode, output: EndpointOutput[T])(
      matcher: PartialFunction[Any, Boolean]
  ): OneOfVariant[T] =
    OneOfVariant(statusCode(code).and(output), matcher.lift.andThen(_.getOrElse(false)))

  @deprecated("Use oneOfVariantValueMatcher", since = "0.19.0")
  def oneOfMappingValueMatcher[T](code: StatusCode, output: EndpointOutput[T])(
      matcher: PartialFunction[Any, Boolean]
  ): OneOfVariant[T] = oneOfVariantValueMatcher(code, output)(matcher)

  /** Create a one-of-variant which `output` if the provided value exactly matches one of the values provided in the second argument list.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantExactMatcher[T: ClassTag](
      output: EndpointOutput[T]
  )(
      firstExactValue: T,
      rest: T*
  ): OneOfVariant[T] =
    oneOfVariantValueMatcher(output)(exactMatch(rest.toSet + firstExactValue))

  /** Create a one-of-variant which uses `output` if the provided value exactly matches one of the values provided in the second argument
    * list. Adds a fixed status-code output with the given value.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantExactMatcher[T: ClassTag](
      code: StatusCode,
      output: EndpointOutput[T]
  )(
      firstExactValue: T,
      rest: T*
  ): OneOfVariant[T] =
    oneOfVariantValueMatcher(code, output)(exactMatch(rest.toSet + firstExactValue))

  @deprecated("Use oneOfVariantExactMatcher", since = "0.19.0")
  def oneOfMappingExactMatcher[T: ClassTag](
      code: StatusCode,
      output: EndpointOutput[T]
  )(
      firstExactValue: T,
      rest: T*
  ): OneOfVariant[T] = oneOfVariantExactMatcher(code, output)(firstExactValue, rest: _*)

  /** Experimental!
    *
    * Create a one-of-variant which uses `output` if the provided value matches the target type, as checked by [[MatchType]]. Instances of
    * [[MatchType]] are automatically derived and recursively check that classes of all fields match, to bypass issues caused by type
    * erasure.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantFromMatchType[T: MatchType](output: EndpointOutput[T]): OneOfVariant[T] =
    oneOfVariantValueMatcher(output)(implicitly[MatchType[T]].partial)

  /** Experimental!
    *
    * Create a one-of-variant which uses `output` if the provided value matches the target type, as checked by [[MatchType]]. Instances of
    * [[MatchType]] are automatically derived and recursively check that classes of all fields match, to bypass issues caused by type
    * erasure. Adds a fixed status-code output with the given value.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariantFromMatchType[T: MatchType](code: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    oneOfVariantValueMatcher(code, output)(implicitly[MatchType[T]].partial)

  @deprecated("Use oneOfVariantFromMatchType", since = "0.19.0")
  def oneOfMappingFromMatchType[T: MatchType](code: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    oneOfVariantFromMatchType(code, output)

  /** Create a fallback variant to be used in [[oneOf]] output descriptions. Multiple such variants can be specified, with different body
    * content types.
    */
  def oneOfDefaultVariant[T](output: EndpointOutput[T]): OneOfVariant[T] = OneOfVariant(output, _ => true)

  @deprecated("Use oneOfDefaultVariant", since = "0.19.0")
  def oneOfDefaultMapping[T](output: EndpointOutput[T]): OneOfVariant[T] = OneOfVariant(output, _ => true)

  /** An empty output. */
  val emptyOutput: EndpointIO.Empty[Unit] = EndpointIO.Empty(Codec.idPlain(), EndpointIO.Info.empty)

  /** An empty output. Useful if one of the [[oneOf]] branches of a coproduct type is a case object that should be mapped to an empty body.
    */
  def emptyOutputAs[T](value: T): EndpointOutput.Basic[T] = emptyOutput.map(_ => value)(_ => ())

  private[tapir] val emptyInput: EndpointInput[Unit] = EndpointIO.Empty(Codec.idPlain(), EndpointIO.Info.empty)

  val infallibleEndpoint: PublicEndpoint[Unit, Nothing, Unit, Any] =
    Endpoint[Unit, Unit, Nothing, Unit, Any](
      emptyInput,
      emptyInput,
      EndpointOutput.Void(),
      emptyOutput,
      EndpointInfo(None, None, None, Vector.empty, deprecated = false, Vector.empty)
    )

  val endpoint: PublicEndpoint[Unit, Unit, Unit, Any] = infallibleEndpoint.copy(errorOutput = emptyOutput)
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
