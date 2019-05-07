import tapir.model.Part

package object tapir extends Tapir {

  // a part which contains one of the types supported by RawValueType
  type RawPart = Part[_]
  type AnyPart = Part[_]
  // mainly used in multipart codecs
  type AnyCodec = Codec[_, _ <: MediaType, _]
  type AnyCodecForMany = CodecForMany[_, _ <: MediaType, _]
  type AnyCodecMeta = CodecMeta[_ <: MediaType, _]
}
