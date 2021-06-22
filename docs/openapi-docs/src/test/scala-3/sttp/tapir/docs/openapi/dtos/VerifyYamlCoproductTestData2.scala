package sttp.tapir.docs.openapi.dtos

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

// TODO: move back to VerifyYamlTest companion after https://github.com/lampepfl/dotty/issues/12849 is fixed
// TODO: remove explicit encoders/decoders/schemas once recursive auto-derivation is supported in magnolia
object VerifyYamlCoproductTestData2 {
  sealed trait Clause
  object Clause {
    implicit def clauseSchema: Schema[Clause] = Schema.derived[Clause]
    implicit def clauseEncoder: Encoder[Clause] = deriveEncoder[Clause]
    implicit def clauseDecoder: Decoder[Clause] = deriveDecoder[Clause]
  }
  case class Expression(v: String) extends Clause
  case class Not(not: Clause) extends Clause
}
