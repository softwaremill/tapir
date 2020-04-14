package sttp

import sttp.model.Part
import sttp.tapir.internal.SeqToParams

package object tapir extends Tapir {
  // a part which contains one of the types supported by BodyType
  type RawPart = Part[_]
  type AnyPart = Part[_]
  // used in multipart codecs
  type AnyListCodec = Codec[_ <: List[_], _, _ <: CodecFormat]

  type MultipartCodec[T] = (RawBodyType.MultipartBody, Codec[Seq[RawPart], T, CodecFormat.MultipartFormData])

  private[tapir] type UnTuple = Any => Vector[Any]
  private[tapir] type MkTuple = Vector[Any] => Any

  object UnTuple {
    val Single: UnTuple = v => Vector(v)
  }

  object MkTuple {
    val Single: MkTuple = vs => SeqToParams(vs)
  }
}
