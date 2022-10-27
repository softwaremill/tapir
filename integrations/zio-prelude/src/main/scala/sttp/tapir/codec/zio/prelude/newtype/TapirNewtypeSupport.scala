package sttp.tapir.codec.zio.prelude.newtype

import sttp.tapir._
import zio.prelude.Newtype

trait TapirNewtypeSupport[A] { self: Newtype[A] =>
  implicit def tapirCodec[L, CF <: CodecFormat](implicit codec: Codec[L, A, CF]): Codec[L, Type, CF] =
    TapirNewtype[A, self.type](self).tapirCodec

  implicit def tapirSchema(implicit schema: Schema[A]): Schema[Type] =
    TapirNewtype[A, self.type](self).tapirSchema
}
