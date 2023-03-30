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

  it should "derive for branched sealed traits where all leafs are objects" in {
    Validator.derivedEnumeration[ColorShades].possibleValues should contain theSameElementsAs Vector(
      ColorShades.LightGrey,
      ColorShades.DarkGrey,
      ColorShades.White,
      ColorShades.Black,
      ColorShades.LightYellow,
      ColorShades.DarkBlue
    )
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


sealed trait ColorShades

object ColorShades {

  sealed trait BW extends ColorShades
  sealed trait Colorful extends ColorShades

  sealed trait Dark extends ColorShades
  sealed trait Light extends ColorShades

  case object LightGrey extends Light, BW
  case object DarkGrey extends Dark, BW
  case object White extends Light, BW
  case object Black extends Dark, BW

  case object LightYellow extends Light, Colorful
  case object DarkBlue extends Dark, Colorful
}