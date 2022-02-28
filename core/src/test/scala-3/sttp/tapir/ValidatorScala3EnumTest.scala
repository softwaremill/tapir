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

}

enum ColorEnum {
  case Green extends ColorEnum
  case Pink extends ColorEnum
}

enum ColorEnumWithParam {
  case Red extends ColorEnumWithParam
  case Green(s: String) extends ColorEnumWithParam
}
