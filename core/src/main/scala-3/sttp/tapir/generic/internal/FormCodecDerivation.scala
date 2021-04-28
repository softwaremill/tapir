package sttp.tapir.generic.internal

import sttp.tapir.generic.Configuration
import sttp.tapir.{Codec, CodecFormat}

trait FormCodecDerivation {
  implicit def formCaseClassCodec[T <: Product with Serializable](implicit
      conf: Configuration
  ): Codec[String, T, CodecFormat.XWwwFormUrlencoded] = ??? // TODO
}
