package tapir

import java.nio.charset.{Charset, StandardCharsets}

import tapir.Codec.PlainCodec
import tapir.CodecForMany.PlainCodecForMany
import tapir.CodecForOptional.PlainCodecForOptional
import tapir.EndpointOutput.StatusMapping
import tapir.model.{Cookie, ServerRequest, SetCookie, SetCookieValue, StatusCode}

import scala.reflect.ClassTag

trait Tapir extends TapirDerivedInputs {
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
  def header(name: String, value: String): EndpointIO.FixedHeader =
    EndpointIO.FixedHeader(name, value, EndpointIO.Info.empty)
  def headers: EndpointIO.Headers = EndpointIO.Headers(EndpointIO.Info.empty)

  def cookie[T: PlainCodecForOptional](name: String): EndpointInput.Cookie[T] =
    EndpointInput.Cookie(name, implicitly[PlainCodecForOptional[T]], EndpointIO.Info.empty)
  def cookies: EndpointIO.Header[List[Cookie]] = header[List[Cookie]](Cookie.HeaderName)
  def setCookie(name: String): EndpointIO.Header[SetCookieValue] = {
    implicit val codec: Codec[SetCookieValue, MediaType.TextPlain, String] = SetCookieValue.setCookieValueCodec(name)
    header[SetCookieValue](SetCookie.HeaderName)
  }
  def setCookies: EndpointIO.Header[List[SetCookie]] = header[List[SetCookie]](SetCookie.HeaderName)

  def body[T, M <: MediaType](implicit tm: CodecForOptional[T, M, _]): EndpointIO.Body[T, M, _] =
    EndpointIO.Body(tm, EndpointIO.Info.empty)

  def stringBody: EndpointIO.Body[String, MediaType.TextPlain, String] = stringBody(StandardCharsets.UTF_8)
  def stringBody(charset: String): EndpointIO.Body[String, MediaType.TextPlain, String] = stringBody(Charset.forName(charset))
  def stringBody(charset: Charset): EndpointIO.Body[String, MediaType.TextPlain, String] =
    EndpointIO.Body(CodecForOptional.fromCodec(Codec.stringCodec(charset)), EndpointIO.Info.empty)

  val htmlBodyUtf8: EndpointIO.Body[String, MediaType.TextHtml, String] =
    EndpointIO.Body(CodecForOptional.fromCodec(Codec.textHtmlCodecUtf8), EndpointIO.Info.empty)

  def plainBody[T](implicit codec: CodecForOptional[T, MediaType.TextPlain, _]): EndpointIO.Body[T, MediaType.TextPlain, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def jsonBody[T](implicit codec: CodecForOptional[T, MediaType.Json, _]): EndpointIO.Body[T, MediaType.Json, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def binaryBody[T](implicit codec: CodecForOptional[T, MediaType.OctetStream, _]): EndpointIO.Body[T, MediaType.OctetStream, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def formBody[T](
      implicit codec: CodecForOptional[T, MediaType.XWwwFormUrlencoded, _]
  ): EndpointIO.Body[T, MediaType.XWwwFormUrlencoded, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)
  def multipartBody[T](
      implicit codec: CodecForOptional[T, MediaType.MultipartFormData, _]
  ): EndpointIO.Body[T, MediaType.MultipartFormData, _] =
    EndpointIO.Body(codec, EndpointIO.Info.empty)

  def streamBody[S](schema: Schema, mediaType: MediaType): StreamingEndpointIO.Body[S, mediaType.type] =
    StreamingEndpointIO.Body(schema, mediaType, EndpointIO.Info.empty)

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
  def statusMapping[O: ClassTag](statusCode: StatusCode, output: EndpointOutput[O]): StatusMapping[O] =
    StatusMapping(Some(statusCode), implicitly[ClassTag[O]], output)
  def statusDefaultMapping[O: ClassTag](output: EndpointOutput[O]): StatusMapping[O] = StatusMapping(None, implicitly[ClassTag[O]], output)

  /**
    * An empty output. Useful if one of `oneOf` branches should be mapped to the status code only.
    */
  def emptyOutput: EndpointOutput[Unit] = EndpointOutput.Multiple(Vector.empty)

  def schemaFor[T: SchemaFor]: Schema = implicitly[SchemaFor[T]].schema

  val endpoint: Endpoint[Unit, Unit, Unit, Nothing] =
    Endpoint[Unit, Unit, Unit, Nothing](
      EndpointInput.Multiple(Vector.empty),
      EndpointOutput.Multiple(Vector.empty),
      EndpointOutput.Multiple(Vector.empty),
      EndpointInfo(None, None, None, Vector.empty)
    )
}

trait TapirDerivedInputs { this: Tapir =>
  def clientIp: EndpointInput[Option[String]] =
    extractFromRequest(
      request =>
        request
          .header("X-Forwarded-For")
          .flatMap(_.split(",").headOption)
          .orElse(request.header("Remote-Address"))
          .orElse(request.header("X-Real-Ip"))
          .orElse(request.connectionInfo.remote.flatMap(a => Option(a.getAddress.getHostAddress)))
    )
}
