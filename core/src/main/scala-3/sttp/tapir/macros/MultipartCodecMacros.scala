package sttp.tapir.macros

import sttp.tapir.MultipartCodec
import sttp.tapir.generic.Configuration

trait MultipartCodecMacros {
  implicit def multipartCaseClassCodec[T <: Product with Serializable](implicit
      conf: Configuration
  ): MultipartCodec[T] = ??? // TODO
}
