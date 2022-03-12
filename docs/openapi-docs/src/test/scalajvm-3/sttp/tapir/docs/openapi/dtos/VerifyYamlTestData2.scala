package sttp.tapir.docs.openapi.dtos

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

// TODO: move back to VerifyYamlTest companion after https://github.com/lampepfl/dotty/issues/12849 is fixed
// TODO: remove explicit encoders/decoders/schemas once recursive auto-derivation is supported in magnolia
object VerifyYamlTestData2 {
  case class F1(data: List[F1])
  object F1 {
    implicit def f1Schema: Schema[F1] = Schema.derived[F1]
    implicit def f1Encoder: Encoder[F1] = deriveEncoder[F1]
    implicit def f1Decoder: Decoder[F1] = deriveDecoder[F1]
  }
}
