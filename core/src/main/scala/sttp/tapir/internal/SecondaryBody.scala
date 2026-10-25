package sttp.tapir.internal

import sttp.tapir.AttributeKey

private[tapir] case class SecondaryBody()

private[tapir] object SecondaryBody {
  val Attribute: AttributeKey[SecondaryBody] = new AttributeKey[SecondaryBody]("sttp.tapir.internal.SecondaryBody")
}
