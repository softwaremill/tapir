package sttp.tapir.codec.zio.prelude.newtype

import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}
import zio.prelude.Newtype

final case class TapirNewtype[A, T <: Newtype[A]](newtype: T) {
  implicit def tapirCodec[L, CF <: CodecFormat](implicit codec: Codec[L, A, CF]): Codec[L, newtype.Type, CF] =
    codec.mapDecode(
      newtype
        .make(_)
        .fold(
          errors => DecodeResult.Multiple(errors.toList),
          DecodeResult.Value(_)
        )
    )(newtype.unwrap)

  implicit def tapirSchema(implicit schema: Schema[A]): Schema[newtype.Type] =
    schema.map(newtype.make(_).toOption)(newtype.unwrap)
}

object TapirNewtype {
  def apply[A]: InferenceHelper[A] = new InferenceHelper[A]
  private[TapirNewtype] final class InferenceHelper[A] {
    def apply[T <: Newtype[A]](t: T): TapirNewtype[A, T] = TapirNewtype[A, T](t)
  }
}
