package sttp.tapir.codec.refined

import sttp.tapir._
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.refineV

trait TapirCodecRefined {
  implicit def codecForRefined[V, P, CF <: CodecFormat, R](implicit tm: Codec[V, CF, R], validator: Validate[V, P]): Codec[V Refined P, CF, R] =
    implicitly[Codec[V, CF, R]]
      .mapDecode { v: V =>
        refineV[P](v) match {
          case Right(refined) => DecodeResult.Value(refined)
          //TODO: exploit error
          case Left(_) => DecodeResult.InvalidValue(List())
        }
      }(_.value)
}
