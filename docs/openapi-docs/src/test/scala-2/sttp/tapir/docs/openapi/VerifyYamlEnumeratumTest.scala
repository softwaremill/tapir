package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import VerifyYamlEnumeratumTest._

class VerifyYamlEnumeratumTest extends AnyFunSuite with Matchers {
  test("use enumeratum validator for array elements") {
    import sttp.tapir.codec.enumeratum._

    val expectedYaml = load("validator/expected_valid_enumeratum.yml")

    val actualYaml =
      OpenAPIDocsInterpreter
        .toOpenAPI(List(endpoint.in("enum-test").out(jsonBody[Enumeratum.FruitWithEnum])), Info("Fruits", "1.0"))
        .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }
}

object VerifyYamlEnumeratumTest {
  object Enumeratum {
    import enumeratum.{Enum, EnumEntry}

    case class FruitWithEnum(fruit: String, amount: Int, fruitType: List[FruitType])

    sealed trait FruitType extends EnumEntry

    object FruitType extends Enum[FruitType] {
      case object APPLE extends FruitType
      case object PEAR extends FruitType
      override def values: scala.collection.immutable.IndexedSeq[FruitType] = findValues
    }
  }
}
