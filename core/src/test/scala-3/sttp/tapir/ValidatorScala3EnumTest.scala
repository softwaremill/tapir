package sttp.tapir

import scala.concurrent.duration.Duration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidatorScala3EnumTest extends AnyFlatSpec with Matchers {
  it should "validate enum" in {
    Validator.derivedEnumeration[ColorEnum].possibleValues should contain theSameElementsAs ColorEnum.values
  }

  it should "not compile for enum with parameter" in {
    assertDoesNotCompile("""
      Validator.derivedEnumeration[ColorEnumWithParam]
    """)
  }

  it should "derive a validator for a string-based union type" in {
    // given
    val validator = Validator.derivedStringBasedUnionEnumeration["Apple" | "Banana"].asInstanceOf[Validator.Primitive[String]]

    // then
    validator.doValidate("Apple") shouldBe ValidationResult.Valid
    validator.doValidate("Banana") shouldBe ValidationResult.Valid
    validator.doValidate("Orange") shouldBe ValidationResult.Invalid()
  }

}

enum ColorEnum {
  case Green extends ColorEnum
  case Pink extends ColorEnum
}

enum ColorEnumWithParam {
  case Red extends ColorEnumWithParam
  case Green(s: String) extends ColorEnumWithParam
}
