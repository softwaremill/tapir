package sttp.tapir

import java.nio.charset.{Charset, StandardCharsets}

import sttp.model.{Cookie, CookieValueWithMeta, CookieWithMeta, Header, HeaderNames, StatusCode}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecForMany.PlainCodecForMany
import sttp.tapir.CodecForOptional.PlainCodecForOptional
import sttp.tapir.EndpointOutput.StatusMapping
import sttp.tapir.internal.{ModifyMacroSupport, StatusMappingMacro}
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.MatchType

import scala.reflect.ClassTag

trait Tapir extends TapirDerivedInputs with ModifyMacroSupport {
  implicit def stringToPath(s: String): EndpointInput[Unit] = EndpointInput.FixedPath(s)

  def path[T: PlainCodec]: EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(implicitly[PlainCodec[T]], None, EndpointIO.Info.empty)
  def path[T: PlainCodec](name: String): EndpointInput.PathCapture[T] =
    EndpointInput.PathCapture(implicitly[PlainCodec[T]], Some(name), EndpointIO.Info.empty)
  def paths: EndpointInput.PathsCapture = EndpointInput.PathsCapture(EndpointIO.Info.empty)

  def query[T: PlainCodecForMany](name: String): EndpointInput.Query[T] =
    EndpointInput.Query(name, implicitly[PlainCodecForMany[T]], EndpointIO.Info.empty)
  def queryParams: EndpointInput.QueryParams = EndpointInput.QueryParams(EndpointIO.Info.empty)

  def header[T: PlainCodecForMany](name: String): EndpointIO.Header[T] =
    EndpointIO.Header(name, implicitly[PlainCodecForMany[T]], EndpointIO.Info.empty)
  def header(h: Header): EndpointIO.FixedHeader =
    EndpointIO.FixedHeader(h.name, h.value, EndpointIO.Info.empty)
  def header(name: String, value: String): EndpointIO.FixedHeader =
    EndpointIO.FixedHeader(name, value, EndpointIO.Info.empty)
  def headers: EndpointIO.Headers = EndpointIO.Headers(EndpointIO.Info.empty)

  def cookie[T: PlainCodecForOptional](name: String): EndpointInput.Cookie[T] =
    EndpointInput.Cookie(name, implicitly[PlainCodecForOptional[T]], EndpointIO.Info.empty)
  def cookies: EndpointIO.Header[List[Cookie]] = header[List[Cookie]](HeaderNames.Cookie)
  def setCookie(name: String): EndpointIO.Header[CookieValueWithMeta] = {
    implicit val codec: CodecForMany[CookieValueWithMeta, CodecFormat.TextPlain, String] =
      CodecForMany.cookieValueWithMetaCodecForMany(name)
    header[CookieValueWithMeta](HeaderNames.SetCookie)
  }
  def setCookies: EndpointIO.Header[List[CookieWithMeta]] = header[List[CookieWithMeta]](HeaderNames.SetCookie)

  def body[T, CF <: CodecFormat](implicit tm: CodecForOptional[T, CF, _]): EndpointIO.Body[T, CF, _] =
    EndpointIO.Body(tm, EndpointIO.Info.empty)

  def stringBody: EndpointIO.Body[String, CodecFormat.TextPlain, String] = stringBody(StandardCharsets.UTF_8)
  def stringBody(charset: String): EndpointIO.Body[String, CodecFormat.TextPlain, String] = stringBody(Charset.forName(charset))
  def stringBody(charset: Charset): EndpointIO.Body[String, CodecFormat.TextPlain, String] =
    EndpointIO.Body(CodecForOptional.fromCodec(Codec.stringCodec(charset)), EndpointIO.Info.empty)

  val htmlBodyUtf8: EndpointIO.Body[String, CodecFormat.TextHtml, String] =
    EndpointIO.Body(CodecForOptional.fromCodec(Codec.textHtmlCodecUtf8), EndpointIO.Info.empty)

  def plainBody[T](implicit codec: CodecForOptional[T, CodecFormat.TextPlain, _]): EndpointIO.Body[T, CodecFormat.TextPlain, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def jsonBody[T](implicit codec: CodecForOptional[T, CodecFormat.Json, _]): EndpointIO.Body[T, CodecFormat.Json, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def binaryBody[T](implicit codec: CodecForOptional[T, CodecFormat.OctetStream, _]): EndpointIO.Body[T, CodecFormat.OctetStream, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def formBody[T](
      implicit codec: CodecForOptional[T, CodecFormat.XWwwFormUrlencoded, _]
  ): EndpointIO.Body[T, CodecFormat.XWwwFormUrlencoded, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def multipartBody[T](
      implicit codec: CodecForOptional[T, CodecFormat.MultipartFormData, _]
  ): EndpointIO.Body[T, CodecFormat.MultipartFormData, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)

  /**
    * @param schema Schema of the body. Note that any schema can be passed here, usually this will be a schema for the
    *               "deserialized" stream.
    * @param format The format of the stream body, which specifies its media type.
    */
  def streamBody[S](schema: Schema[_], format: CodecFormat): StreamingEndpointIO.Body[S, format.type] =
    StreamingEndpointIO.Body(schema, format, EndpointIO.Info.empty)

  def auth: TapirAuth.type = TapirAuth

  /**
    * Extract a value from a server request. This input is only used by server interpreters, it is ignored by
    * documentation interpreters and the provided value is discarded by client interpreters.
    */
  def extractFromRequest[T](f: ServerRequest => T): EndpointInput.ExtractFromRequest[T] = EndpointInput.ExtractFromRequest(f)

  def statusCode: EndpointOutput.StatusCode = EndpointOutput.StatusCode()
  def statusCode(statusCode: StatusCode): EndpointOutput.FixedStatusCode = EndpointOutput.FixedStatusCode(statusCode, EndpointIO.Info.empty)

  /**
    * Maps status codes to outputs. All outputs must have a common supertype (`I`). Typically, the supertype is a sealed
    * trait, and the mappings are implementing cases classes.
    *
    * Note that exhaustiveness of the mappings is not checked (that all subtypes of `I` are covered).
    */
  def oneOf[I](firstCase: StatusMapping[_ <: I], otherCases: StatusMapping[_ <: I]*): EndpointOutput.OneOf[I] =
    EndpointOutput.OneOf[I](firstCase +: otherCases)

  /**
    * Create a status mapping which uses `statusCode` and `output` if the class of the provided value (when interpreting
    * as a server) matches the runtime class of `O`.
    *
    * This will fail at compile-time if the type erasure of `O` is different from `O`, as a runtime check in this
    * situation would give invalid results. In such cases, use [[statusMappingClassMatcher]],
    * [[statusMappingValueMatcher]] or [[statusMappingFromMatchType]] instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def statusMapping[O: ClassTag](statusCode: StatusCode, output: EndpointOutput[O]): StatusMapping[O] =
    macro StatusMappingMacro.classMatcherIfErasedSameAsType[O]

  /**
    * Create a status mapping which uses `statusCode` and `output` if the class of the provided value (when interpreting
    * as a server) matches the given `runtimeClass`. Note that this does not take into account type erasure.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def statusMappingClassMatcher[O](
      statusCode: StatusCode,
      output: EndpointOutput[O],
      runtimeClass: Class[_]
  ): StatusMapping[O] = {
    StatusMapping(Some(statusCode), output, { a: Any =>
      runtimeClass.isInstance(a)
    })
  }

  /**
    * Create a status mapping which uses `statusCode` and `output` if the provided value (when interpreting as a server
    * matches the `matcher` predicate.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def statusMappingValueMatcher[O](statusCode: StatusCode, output: EndpointOutput[O])(
      matcher: PartialFunction[Any, Boolean]
  ): StatusMapping[O] =
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
  def statusMappingFromMatchType[O: MatchType](statusCode: StatusCode, output: EndpointOutput[O]): StatusMapping[O] =
    statusMappingValueMatcher(statusCode, output)(implicitly[MatchType[O]].partial)

  /**
    * Create a fallback mapping to be used in [[oneOf]] output descriptions.
    */
  def statusDefaultMapping[O](output: EndpointOutput[O]): StatusMapping[O] = {
    StatusMapping(None, output, _ => true)
  }

  /**
    * An empty output. Useful if one of `oneOf` branches should be mapped to the status code only.
    */
  def emptyOutput: EndpointOutput[Unit] = EndpointOutput.Multiple(Vector.empty)

  def schemaFor[T: Schema]: Schema[T] = implicitly[Schema[T]]

  val infallibleEndpoint: Endpoint[Unit, Nothing, Unit, Nothing] =
    Endpoint[Unit, Nothing, Unit, Nothing](
      EndpointInput.Multiple(Vector.empty),
      EndpointOutput.Void(),
      EndpointOutput.Multiple(Vector.empty),
      EndpointInfo(None, None, None, Vector.empty, deprecated = false)
    )

  val endpoint: Endpoint[Unit, Unit, Unit, Nothing] = infallibleEndpoint.copy(errorOutput = EndpointOutput.Multiple(Vector.empty))
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
