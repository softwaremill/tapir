package sttp.tapir.docs.openapi.dtos

import sttp.tapir.Schema.annotations.default
import sttp.tapir.tests.data.FruitAmount

// TODO: move back to VerifyYamlTest companion after https://github.com/lampepfl/dotty/issues/12849 is fixed
object VerifyYamlTestData {
  case class G[T](data: T)
  case class ObjectWrapper(value: FruitAmount)
  case class ObjectWithList(data: List[FruitAmount])
  case class ObjectWithSet(data: Set[FruitAmount])
  case class ObjectWithOption(data: Option[FruitAmount])
  case class ObjectWithDefaults(@default("foo") name: String, @default(12) count: Int)
}
