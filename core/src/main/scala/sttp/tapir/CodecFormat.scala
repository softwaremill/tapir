package sttp.tapir

import sttp.model.MediaType

/** Specifies the format of the encoded values. Each variant must be a proper type so that it can be used as a discriminator for different
  * (implicit) instances of [[Codec]] values.
  */
trait CodecFormat {
  def mediaType: MediaType
}

object CodecFormat {
  case class Json() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ApplicationJson
  }

  case class Xml() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ApplicationXml
  }

  case class TextPlain() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextPlain
  }

  case class TextHtml() extends CodecFormat {
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

  case class Zip() extends CodecFormat {
    override val mediaType: MediaType = MediaType.ApplicationZip
  }

  case class TextEventStream() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextEventStream
  }

  case class TextJavascript() extends CodecFormat {
    override val mediaType: MediaType = MediaType.TextJavascript
  }
}
