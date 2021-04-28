package sttp.tapir.generic.internal

import sttp.tapir.MultipartCodec
import sttp.tapir.generic.Configuration

trait MultipartCodecDerivation {
  implicit def multipartCaseClassCodec[T <: Product with Serializable](implicit
      conf: Configuration
  ): MultipartCodec[T] = ??? // TODO
}
