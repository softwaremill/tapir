package sttp

import sttp.model.Part

package object tapir extends Tapir with ModifyMacroSupport {
  // a part which contains one of the types supported by RawValueType
  type RawPart = Part[_]
  type AnyPart = Part[_]
  // mainly used in multipart codecs
  type AnyCodec = Codec[_, _ <: CodecFormat, _]
  type AnyCodecForMany = CodecForMany[_, _ <: CodecFormat, _]
  type AnyCodecMeta = CodecMeta[_, _ <: CodecFormat, _]
}
