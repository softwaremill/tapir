package sttp.tapir.docs.openapi.dtos

import sttp.tapir.tests.data.FruitAmount

// TODO: move back to VerifyYamlTest companion after https://github.com/lampepfl/dotty/issues/12849 is fixed
object VerifyYamlValidatorTestData {
  case class ObjectWithList(data: List[FruitAmount])
  case class ObjectWithStrings(data: List[String])
  case class MyClass(myAttribute: Int)
}
