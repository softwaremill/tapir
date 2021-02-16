package sttp.tapir.codec.newtype

import io.estatico.newtype.Coercible
import sttp.tapir._

trait TapirCodecNewType {
  implicit def schemaForNewType[A, B](implicit ev: Coercible[Schema[A], Schema[B]], schema: Schema[A]): Schema[B] = ev(schema)

  implicit def codecForNewType[L, A, B, CF <: CodecFormat](implicit
      ev1: Coercible[A, B],
      ev2: Coercible[B, A],
      codec: Codec[L, A, CF]
  ): Codec[L, B, CF] = codec.map(ev1(_))(ev2(_))
}
