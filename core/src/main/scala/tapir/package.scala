import tapir.model.Part

package object tapir extends Tapir {

  // mainly used in multipart codecs
  type RawPart = Part[_]
  type AnyPart = Part[_]
  type AnyCodec = Codec[_, _ <: MediaType, _]
  type AnyCodecForMany = CodecForMany[_, _ <: MediaType, _]
  type AnyCodecMeta = CodecMeta[_ <: MediaType, _]
}
