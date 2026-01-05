package sttp.tapir.codec.zio.prelude.newtype

import zio.prelude.Assertion.{divisibleBy, endsWith, greaterThan, startsWith}
import zio.prelude.{Newtype, Subtype, NewtypeCustom}

object TapirNewtypeTestFixture {
  object StringNewtype extends Newtype[String] {
    override inline def assertion = startsWith("foo") && endsWith("baz")
  }
  type StringNewtype = StringNewtype.Type

  object IntSubtype extends Subtype[Int] {
    override inline def assertion = divisibleBy(2) && greaterThan(5)
  }
  type IntSubtype = IntSubtype.Type

  object Palindrome extends NewtypeCustom[String] {
    protected def validate(value: String) = PalindromeValidator.validate(value)
    protected inline def validateInline(inline value: String) = ${ PalindromeValidator.validateInlineImpl('value) }
  }
  type Palindrome = Palindrome.Type
}
