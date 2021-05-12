package sttp.tapir

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidatorTest2 extends AnyFlatSpec with Matchers {
  it should "validate coproduct enum" in {
    Validator.derivedEnumeration[Color](Blue) shouldBe empty
  }

  it should "validate enum" in {
    Validator.derivedEnumeration[ColorEnum](ColorEnum.Green) shouldBe empty
  }

  it should "not compile for enum with parameter" in {
    assertDoesNotCompile("""
      Validator.derivedEnumeration[ColorEnumWithParam](ColorEnumWithParam.Red) shouldBe empty
    """)
  }

}

enum ColorEnum { 
  case Green  extends ColorEnum
  case Pink   extends ColorEnum
}

enum ColorEnumWithParam {
  case Red              extends ColorEnumWithParam
  case Green(s: String) extends ColorEnumWithParam
}
