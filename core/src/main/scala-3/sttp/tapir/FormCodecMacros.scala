package sttp.tapir

import sttp.tapir.generic.Configuration

trait FormCodecMacros {
  implicit def formCaseClassCodec[T <: Product with Serializable](implicit
      conf: Configuration
  ): Codec[String, T, CodecFormat.XWwwFormUrlencoded] = ??? // TODO
}
