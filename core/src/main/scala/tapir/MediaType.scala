package tapir

import java.nio.charset.{Charset, StandardCharsets}

trait MediaType {
  def mediaTypeNoParams: String
  def mediaType: String = mediaTypeNoParams
}

object MediaType {

  /**
    * Create a custom media type.
    */
  def apply(mediaType: String): MediaType = {
    val mt = mediaType
    new MediaType {
      override def mediaTypeNoParams: String = mt
    }
  }

  case class Json() extends MediaType {
    override val mediaTypeNoParams: String = "application/json"
  }

  case class TextPlain(charset: Charset = StandardCharsets.UTF_8) extends MediaType {
    override val mediaTypeNoParams: String = s"text/plain"
    override val mediaType: String = s"$mediaTypeNoParams; charset=${charset.name()}"
  }

  case class OctetStream() extends MediaType {
    override val mediaTypeNoParams: String = "application/octet-stream"
  }

  case class XWwwFormUrlencoded() extends MediaType {
    override val mediaTypeNoParams: String = "application/x-www-form-urlencoded"
  }

  case class MultipartFormData() extends MediaType {
    override val mediaTypeNoParams: String = "multipart/form-data"
  }
}
