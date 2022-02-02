package sttp.tapir

import scala.concurrent.duration.Duration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.SchemaType.SString

class ValidatorScala3EnumTest extends AnyFlatSpec with Matchers {
  it should "validate enum" in {
    Validator.derivedEnumeration[ColorEnum].possibleValues should contain theSameElementsAs ColorEnum.values
  }

  it should "not compile for enum with parameter" in {
    assertDoesNotCompile("""
      Validator.derivedEnumeration[ColorEnumWithParam]
    """)
  }

  it should "derive schema for enum" in {
    import sttp.tapir.generic.auto._
    implicitly[Schema[ColorEnum]] shouldBe Schema(SString())
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
