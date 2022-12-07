package sttp.tapir.serverless.aws.cdk.internal

sealed abstract class Segment protected (value: String) {
  def toString: String

  def raw: String = value
}

object Segment {

  case class Fixed private (value: String) extends Segment(value) {
    override def toString: String = raw
  }

  object Fixed {
    def apply(value: String): Option[Fixed] = {
      if (value.isEmpty) None
      else Some(new Fixed(value))
    }
  }

  case class Parameter private (value: String) extends Segment(value) {
    override def toString: String = s"{$raw}"
  }

  object Parameter {
    def apply(value: String): Option[Parameter] = {
      if (value.isEmpty) None
      else Some(new Parameter(value))
    }
  }

  /** This do not enforce any strict rules or logic. Preprocessed and encoded segments derive from core tapir Endpoint. Duplicating any url
    * segments processing rules would be rather harmful here. This is not responsibility of this module at all. Segment just introduce
    * explicit differentiation between fixed path and parameter for clarity purposes.
    *
    * There is no single form of validation in place
    */
  def apply(value: String): Option[Segment] = {

    if (value.isEmpty) return None

    "^\\{(.+)\\}$".r.findFirstMatchIn(value) match {
      case None    => Fixed(value)
      case Some(m) => Parameter(m.group(1))
    }
  }
}
