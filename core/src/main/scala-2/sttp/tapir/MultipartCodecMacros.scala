package sttp.tapir

import sttp.tapir.generic.Configuration
import sttp.tapir.generic.internal.MultipartCodecMacro

trait MultipartCodecMacros {
  implicit def multipartCaseClassCodec[T <: Product with Serializable](implicit
      conf: Configuration
  ): MultipartCodec[T] =
    macro MultipartCodecMacro.generateForCaseClass[T]
}
