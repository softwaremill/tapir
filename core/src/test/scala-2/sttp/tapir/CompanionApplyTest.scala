package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object CompanionApplyTestFixtures {
  case class Normalized(s: String)
  object Normalized {
    def apply(s: String): Normalized = new Normalized(s.trim)
  }

  case class FormWithApply(a: String, b: Int)
  object FormWithApply {
    def apply(a: String, b: Int): FormWithApply = new FormWithApply(a.trim, b)
  }

  case class Guarded private (i: Int)

  case class FormWithOverload(a: String, b: Int)
  object FormWithOverload {
    def apply(a: String): FormWithOverload = new FormWithOverload(a, 0)
  }
}

// on Scala 2, macro-generated mappings construct case classes through the companion's `apply`; these tests document
// that a custom same-signature `apply` is used, and that case classes with private constructors remain supported
// (on Scala 3, mappings are `Mirror`-based and invoke the constructor directly)
class CompanionApplyTest extends AnyFlatSpec with Matchers {
  import CompanionApplyTestFixtures._

  "mapTo" should "use a custom companion apply if one is defined" in {
    val codec = plainBody[String].mapTo[Normalized].codec

    codec.decode(" x ") shouldBe DecodeResult.Value(Normalized("x"))
  }

  "mapTo" should "compile for a case class with a private constructor" in {
    plainBody[Int].mapTo[Guarded]
  }

  "formBody" should "use a custom companion apply if one is defined" in {
    import sttp.tapir.generic.auto._

    val codec = implicitly[Codec[String, FormWithApply, CodecFormat.XWwwFormUrlencoded]]

    codec.decode("a=+x+&b=1") shouldBe DecodeResult.Value(FormWithApply("x", 1))
  }

  "formBody" should "compile when the companion defines additional apply overloads" in {
    import sttp.tapir.generic.auto._

    val codec = implicitly[Codec[String, FormWithOverload, CodecFormat.XWwwFormUrlencoded]]

    codec.decode("a=x&b=1") shouldBe DecodeResult.Value(FormWithOverload("x", 1))
  }
}
