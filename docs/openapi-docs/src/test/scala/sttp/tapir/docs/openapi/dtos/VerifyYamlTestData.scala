package sttp.tapir.docs.openapi.dtos

import sttp.tapir.tests.FruitAmount

// TODO: move back to VerifyYamlTest companion after https://github.com/lampepfl/dotty/issues/12849 is fixed
object VerifyYamlTestData {
  case class G[T](data: T)
  case class ObjectWrapper(value: FruitAmount)
  case class ObjectWithList(data: List[FruitAmount])
  case class ObjectWithOption(data: Option[FruitAmount])
}
