package sttp.tapir

import sttp.tapir.generic.Configuration
import sttp.tapir.generic.internal.FormCodecMacro

trait FormCodecMacros {
  implicit def formCaseClassCodec[T <: Product with Serializable](implicit
      conf: Configuration
  ): Codec[String, T, CodecFormat.XWwwFormUrlencoded] =
    macro FormCodecMacro.generateForCaseClass[T]
}
