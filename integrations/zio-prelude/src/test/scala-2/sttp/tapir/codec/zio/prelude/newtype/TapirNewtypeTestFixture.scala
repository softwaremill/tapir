package sttp.tapir.codec.zio.prelude.newtype

import zio.prelude.Assertion.{divisibleBy, endsWith, greaterThan, startsWith}
import zio.prelude.{Newtype, Subtype}

object TapirNewtypeTestFixture {
  object StringNewtype extends Newtype[String] {
    override def assertion = assert(startsWith("foo") && endsWith("baz"))
  }
  type StringNewtype = StringNewtype.Type

  object IntSubtype extends Subtype[Int] {
    override def assertion = assert(divisibleBy(2) && greaterThan(5))
  }
  type IntSubtype = IntSubtype.Type
}
