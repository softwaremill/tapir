package sttp.tapir.codec.monix.newtype

import monix.newtypes.{HasBuilder, HasExtractor}
import sttp.tapir._

trait TapirCodecMonixNewType {
  implicit def schemaForMonixNewType[T, S](implicit ev1: HasExtractor.Aux[T, S], ev2: HasBuilder.Aux[T, S], schema: Schema[S]): Schema[T] =
    schema.map(ev2.build(_).toOption)(ev1.extract(_))

  implicit def codecForMonixNewType[L, A, B, CF <: CodecFormat](implicit
      ev1: HasExtractor.Aux[B, A],
      ev2: HasBuilder.Aux[B, A],
      codec: Codec[L, A, CF]
  ): Codec[L, B, CF] =
    codec.mapDecode(v => DecodeResult.fromEitherString(v.toString, ev2.build(v).left.map(_.toReadableString)))(ev1.extract(_))
}
