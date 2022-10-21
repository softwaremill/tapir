package sttp.tapir.codec.zio.prelude.newtype

import zio.prelude.Assertion.{divisibleBy, endsWith, greaterThan, startsWith}
import zio.prelude.{Newtype, Subtype}

object TapirNewtypeSupportTestFixture {
  object StringNewtypeWithMixin extends Newtype[String] with TapirNewtypeSupport[String] {
    override def assertion = assert(startsWith("foo") && endsWith("baz"))
  }
  type StringNewtypeWithMixin = StringNewtypeWithMixin.Type

  object IntSubtypeWithMixin extends Subtype[Int] with TapirNewtypeSupport[Int] {
    override def assertion = assert(divisibleBy(2) && greaterThan(5))
  }
  type IntSubtypeWithMixin = IntSubtypeWithMixin.Type
}
