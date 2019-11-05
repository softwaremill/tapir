package tapir

import java.nio.charset.{Charset, StandardCharsets}

import sttp.model.MediaType

/**
  * Specifies the format of the encoded values. Each variant must be a proper type so that it can be used as a
  * discriminator for different (implicit) instances of [[Codec]] values.
  */
trait CodecFormat {
  def mediaType: MediaType
}

object CodecFormat {
  case class Json() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ApplicationJson
  }

  case class TextPlain(charset: Charset = StandardCharsets.UTF_8) extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextPlain
  }

  case class TextHtml(charset: Charset = StandardCharsets.UTF_8) extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextHtml
  }

  case class OctetStream() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ApplicationOctetStream
  }

  case class XWwwFormUrlencoded() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ApplicationXWwwFormUrlencoded
  }

  case class MultipartFormData() extends CodecFormat {
    override val mediaType: MediaType = MediaType.MultipartFormData
  }
}
