package sttp.tapir.codec.zio.prelude.newtype

import zio.prelude.Assertion.{divisibleBy, endsWith, greaterThan, startsWith}
import zio.prelude.{Newtype, Subtype}

object TapirNewtypeSupportTestFixture {
  object StartsWithFooEndsWithBaz extends Newtype[String] with TapirNewtypeSupport[String] {
    override def assertion = assert(startsWith("foo") && endsWith("baz"))
  }
  type StartsWithFooEndsWithBaz = StartsWithFooEndsWithBaz.Type

  object EvenGreaterThanFive extends Subtype[Int] with TapirNewtypeSupport[Int] {
    override def assertion = assert(divisibleBy(2) && greaterThan(5))
  }
  type EvenGreaterThanFive = EvenGreaterThanFive.Type
}
