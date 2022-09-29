package sttp.tapir.codec.zio.prelude.newtype

import sttp.tapir._
import zio.prelude.Newtype

trait TapirNewtypeSupport[A] { self: Newtype[A] =>
  implicit def tapirCodec[L, CF <: CodecFormat](implicit codec: Codec[L, A, CF]): Codec[L, Type, CF] =
    codec.mapDecode(
      make(_).fold(
        errors => DecodeResult.Multiple(errors.toList),
        DecodeResult.Value(_)
      )
    )(unwrap)

  implicit def tapirSchema(implicit schema: Schema[A]): Schema[Type] =
    schema.map(make(_).toOption)(unwrap)
}
