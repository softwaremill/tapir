package sttp.tapir.macros

import sttp.tapir.generic.Configuration
import sttp.tapir.internal.FormCodecMacro
import sttp.tapir.{Codec, CodecFormat}

trait FormCodecMacros {
  implicit def formCaseClassCodec[T <: Product with Serializable](implicit
      conf: Configuration
  ): Codec[String, T, CodecFormat.XWwwFormUrlencoded] =
    macro FormCodecMacro.generateForCaseClass[T]
}
