package sttp.tapir

import java.nio.charset.{Charset, StandardCharsets}

import sttp.model.{Cookie, CookieValueWithMeta, CookieWithMeta, Header, HeaderNames, MultiQueryParams, StatusCode}
import sttp.tapir.CodecFormat.{Json, OctetStream, TextPlain}
import sttp.tapir.EndpointOutput.StatusMapping
import sttp.tapir.internal.{ModifyMacroSupport, StatusMappingMacro}
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.MatchType
import sttp.tapir.internal._

import scala.reflect.ClassTag

trait Tapir extends TapirDerivedInputs with ModifyMacroSupport {
  implicit def stringToPath(s: String): EndpointInput.FixedPath[Unit] = EndpointInput.FixedPath(s, Codec.idPlain(), EndpointIO.Info.empty)

  def path[T: Codec[String, *, TextPlain]]: EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(None, implicitly, EndpointIO.Info.empty)
  def path[T: Codec[String, *, TextPlain]](name: String): EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(Some(name), implicitly, EndpointIO.Info.empty)
  def paths: EndpointInput.PathsCapture[List[String]] = EndpointInput.PathsCapture(Codec.idPlain(), EndpointIO.Info.empty)

  def query[T: Codec[List[String], *, TextPlain]](name: String): EndpointInput.Query[T] =
    EndpointInput.Query(name, implicitly, EndpointIO.Info.empty)
  def queryParams: EndpointInput.QueryParams[MultiQueryParams] = EndpointInput.QueryParams(Codec.idPlain(), EndpointIO.Info.empty)

  def header[T: Codec[List[String], *, TextPlain]](name: String): EndpointIO.Header[T] =
    EndpointIO.Header(name, implicitly, EndpointIO.Info.empty)
  def header(h: Header): EndpointIO.FixedHeader[Unit] = EndpointIO.FixedHeader(h, Codec.idPlain(), EndpointIO.Info.empty)
  def header(name: String, value: String): EndpointIO.FixedHeader[Unit] = header(sttp.model.Header.notValidated(name, value))
  def headers: EndpointIO.Headers[List[sttp.model.Header]] = EndpointIO.Headers(Codec.idPlain(), EndpointIO.Info.empty)

  def cookie[T: Codec[Option[String], *, TextPlain]](name: String): EndpointInput.Cookie[T] =
    EndpointInput.Cookie(name, implicitly, EndpointIO.Info.empty)
  def cookies: EndpointIO.Header[List[Cookie]] = header[List[String]](HeaderNames.Cookie).map(Codec.cookiesCodec)
  def setCookie(name: String): EndpointIO.Header[CookieValueWithMeta] = {
    setCookies.mapDecode(cs =>
      cs.filter(_.name == name) match {
        case Nil     => DecodeResult.Missing
        case List(c) => DecodeResult.Value(c.valueWithMeta)
        case l       => DecodeResult.Multiple(l.map(_.toString))
      }
    )(cv => List(CookieWithMeta(name, cv)))
  }
  def setCookies: EndpointIO.Header[List[CookieWithMeta]] = header[List[String]](HeaderNames.SetCookie).map(Codec.cookiesWithMetaCodec)

  def stringBody: EndpointIO.Body[String, String] = stringBody(StandardCharsets.UTF_8)
  def stringBody(charset: String): EndpointIO.Body[String, String] = stringBody(Charset.forName(charset))
  def stringBody(charset: Charset): EndpointIO.Body[String, String] =
    EndpointIO.Body(RawBodyType.StringBody(charset), Codec.string, EndpointIO.Info.empty)

  val htmlBodyUtf8: EndpointIO.Body[String, String] =
    EndpointIO.Body(RawBodyType.StringBody(StandardCharsets.UTF_8), Codec.string, EndpointIO.Info.empty)

  def plainBody[T: Codec[String, *, TextPlain]]: EndpointIO.Body[String, T] = plainBody(StandardCharsets.UTF_8)
  def plainBody[T: Codec[String, *, TextPlain]](charset: Charset): EndpointIO.Body[String, T] =
    EndpointIO.Body(RawBodyType.StringBody(charset), implicitly, EndpointIO.Info.empty)

  /**
    * Json codecs are usually derived from json-library-specific implicits. That's why integrations with
    * various json libraries define `jsonBody` methods, which directly require the library-specific implicits.
    *
    * If you have a custom json codec, you should use this method instead.
    */
  def anyJsonBody[T: Codec[String, *, Json]]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(implicitly[Codec[String, T, Json]])

  def rawBinaryBody[R: RawBodyType.Binary](implicit codec: Codec[R, R, OctetStream]): EndpointIO.Body[R, R] =
    EndpointIO.Body(implicitly[RawBodyType.Binary[R]], codec, EndpointIO.Info.empty)
  def binaryBody[R: RawBodyType.Binary, T: Codec[R, *, OctetStream]]: EndpointIO.Body[R, T] =
    EndpointIO.Body(implicitly[RawBodyType.Binary[R]], implicitly[Codec[R, T, OctetStream]], EndpointIO.Info.empty)

  def formBody[T: Codec[String, *, CodecFormat.XWwwFormUrlencoded]]: EndpointIO.Body[String, T] =
    anyFromUtf8StringBody[T, CodecFormat.XWwwFormUrlencoded](implicitly)
  def formBody[T: Codec[String, *, CodecFormat.XWwwFormUrlencoded]](charset: Charset): EndpointIO.Body[String, T] =
    anyFromStringBody[T, CodecFormat.XWwwFormUrlencoded](implicitly, charset)

  val multipartBody: EndpointIO.Body[Seq[RawPart], Seq[AnyPart]] = multipartBody(MultipartCodec.Default)
  def multipartBody[T](implicit multipartCodec: MultipartCodec[T]): EndpointIO.Body[Seq[RawPart], T] =
    EndpointIO.Body(multipartCodec._1, multipartCodec._2, EndpointIO.Info.empty)

  /**
    * @param schema Schema of the body. Note that any schema can be passed here, usually this will be a schema for the
    *               "deserialized" stream.
    * @param charset An optional charset of the resulting stream's data, to be used in the content type.
    */
  def streamBody[S](schema: Schema[_], format: CodecFormat, charset: Option[Charset] = None): StreamingEndpointIO.Body[S, S] =
    StreamingEndpointIO.Body(Codec.id(format, Some(schema.as[S])), EndpointIO.Info.empty, charset)

  /**
    * A body in any format, read using the given `codec`, from a raw string read using UTF-8.
    */
  def anyFromUtf8StringBody[T, CF <: CodecFormat](codec: Codec[String, T, CF]): EndpointIO.Body[String, T] =
    anyFromStringBody[T, CF](codec, StandardCharsets.UTF_8)

  /**
    * A body in any format, read using the given `codec`, from a raw string read using `charset`.
    */
  def anyFromStringBody[T, CF <: CodecFormat](codec: Codec[String, T, CF], charset: Charset): EndpointIO.Body[String, T] =
    EndpointIO.Body(RawBodyType.StringBody(charset), codec, EndpointIO.Info.empty)

  def auth: TapirAuth.type = TapirAuth

  /**
    * Extract a value from a server request. This input is only used by server interpreters, it is ignored by
    * documentation interpreters and the provided value is discarded by client interpreters.
    */
  def extractFromRequest[T](f: ServerRequest => T): EndpointInput.ExtractFromRequest[T] =
    EndpointInput.ExtractFromRequest(Codec.idPlain[ServerRequest]().map(f)(_ => null), EndpointIO.Info.empty)

  def statusCode: EndpointOutput.StatusCode[sttp.model.StatusCode] =
    EndpointOutput.StatusCode(Map.empty, Codec.idPlain(), EndpointIO.Info.empty)
  def statusCode(statusCode: sttp.model.StatusCode): EndpointOutput.FixedStatusCode[Unit] =
    EndpointOutput.FixedStatusCode(statusCode, Codec.idPlain(), EndpointIO.Info.empty)

  /**
    * Maps status codes to outputs. All outputs must have a common supertype (`T`). Typically, the supertype is a sealed
    * trait, and the mappings are implementing cases classes.
    *
    * Note that exhaustiveness of the mappings is not checked (that all subtypes of `T` are covered).
    */
  def oneOf[T](firstCase: StatusMapping[_ <: T], otherCases: StatusMapping[_ <: T]*): EndpointOutput.OneOf[T, T] =
    EndpointOutput.OneOf[T, T](firstCase +: otherCases, Codec.idPlain())

  /**
    * Create a status mapping which uses `statusCode` and `output` if the class of the provided value (when interpreting
    * as a server) matches the runtime class of `T`.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this
    * situation would give invalid results. In such cases, use [[statusMappingClassMatcher]],
    * [[statusMappingValueMatcher]] or [[statusMappingFromMatchType]] instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def statusMapping[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): StatusMapping[T] =
    macro StatusMappingMacro.classMatcherIfErasedSameAsType[T]

  /**
    * Create a status mapping which uses `statusCode` and `output` if the class of the provided value (when interpreting
    * as a server) matches the given `runtimeClass`. Note that this does not take into account type erasure.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def statusMappingClassMatcher[T](
      statusCode: StatusCode,
      output: EndpointOutput[T],
      runtimeClass: Class[_]
  ): StatusMapping[T] = {
    StatusMapping(Some(statusCode), output, { a: Any => runtimeClass.isInstance(a) })
  }

  /**
    * Create a status mapping which uses `statusCode` and `output` if the provided value (when interpreting as a server
    * matches the `matcher` predicate.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def statusMappingValueMatcher[T](statusCode: StatusCode, output: EndpointOutput[T])(
      matcher: PartialFunction[Any, Boolean]
  ): StatusMapping[T] =
    StatusMapping(Some(statusCode), output, matcher.lift.andThen(_.getOrElse(false)))

  /**
    * Experimental!
    *
    * Create a status mapping which uses `statusCode` and `output` if the provided value matches the target type, as
    * checked by [[MatchType]]. Instances of [[MatchType]] are automatically derived and recursively check that
    * classes of all fields match, to bypass issues caused by type erasure.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def statusMappingFromMatchType[T: MatchType](statusCode: StatusCode, output: EndpointOutput[T]): StatusMapping[T] =
    statusMappingValueMatcher(statusCode, output)(implicitly[MatchType[T]].partial)

  /**
    * Create a fallback mapping to be used in [[oneOf]] output descriptions.
    */
  def statusDefaultMapping[T](output: EndpointOutput[T]): StatusMapping[T] = {
    StatusMapping(None, output, _ => true)
  }

  /**
    * An empty output. Useful if one of `oneOf` branches should be mapped to the status code only.
    */
  def emptyOutput: EndpointOutput[Unit] = EndpointOutput.Tuple(Vector.empty)

  def schemaFor[T: Schema]: Schema[T] = implicitly[Schema[T]]

  val infallibleEndpoint: Endpoint[Unit, Nothing, Unit, Nothing] =
    Endpoint[Unit, Nothing, Unit, Nothing](
      EndpointInput.Tuple(Vector.empty),
      EndpointOutput.Void(),
      EndpointOutput.Tuple(Vector.empty),
      EndpointInfo(None, None, None, Vector.empty, deprecated = false)
    )

  val endpoint: Endpoint[Unit, Unit, Unit, Nothing] = infallibleEndpoint.copy(errorOutput = EndpointOutput.Tuple(Vector.empty))
}

trait TapirDerivedInputs { this: Tapir =>
  def clientIp: EndpointInput[Option[String]] =
    extractFromRequest(request =>
      request
        .header("X-Forwarded-For")
        .flatMap(_.split(",").headOption)
        .orElse(request.header("Remote-Address"))
        .orElse(request.header("X-Real-Ip"))
        .orElse(request.connectionInfo.remote.flatMap(a => Option(a.getAddress.getHostAddress)))
    )
}
