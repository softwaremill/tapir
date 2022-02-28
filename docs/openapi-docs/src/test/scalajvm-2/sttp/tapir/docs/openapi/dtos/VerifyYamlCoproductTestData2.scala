package sttp.tapir.docs.openapi.dtos

object VerifyYamlCoproductTestData2 {
  sealed trait Clause
  case class Expression(v: String) extends Clause
  case class Not(not: Clause) extends Clause
}
