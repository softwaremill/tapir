package sttp.tapir.codec.zio.prelude.newtype

import zio.prelude.Assertion.{divisibleBy, endsWith, greaterThan, startsWith}
import zio.prelude.{Newtype, NewtypeCustom, Subtype}

object TapirNewtypeSupportTestFixture {
  object StringNewtypeWithMixin extends Newtype[String] with TapirNewtypeSupport[String] {
    override inline def assertion = startsWith("foo") && endsWith("baz")
  }
  type StringNewtypeWithMixin = StringNewtypeWithMixin.Type

  object IntSubtypeWithMixin extends Subtype[Int] with TapirNewtypeSupport[Int] {
    override inline def assertion = divisibleBy(2) && greaterThan(5)
  }
  type IntSubtypeWithMixin = IntSubtypeWithMixin.Type

  object PalindromeWithMixin extends NewtypeCustom[String] with TapirNewtypeSupport[String] {
    protected def validate(value: String) = PalindromeValidator.validate(value)
    protected inline def validateInline(inline value: String) = ${ PalindromeValidator.validateInlineImpl('value) }
  }
  type PalindromeWithMixin = PalindromeWithMixin.Type
}
