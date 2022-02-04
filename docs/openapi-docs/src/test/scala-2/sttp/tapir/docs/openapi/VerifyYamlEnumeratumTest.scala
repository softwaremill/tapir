package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.annotations.description
import sttp.tapir._
import sttp.tapir.docs.openapi.VerifyYamlEnumeratumTest.Enumeratum
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._

class VerifyYamlEnumeratumTest extends AnyFunSuite with Matchers {
  test("use enumeratum validator for array elements") {
    import sttp.tapir.codec.enumeratum._

    val expectedYaml = load("validator/expected_valid_enumeratum.yml")

    val actualYaml =
      OpenAPIDocsInterpreter()
        .toOpenAPI(List(endpoint.in("enum-test").out(jsonBody[Enumeratum.FruitWithEnum])), Info("Fruits", "1.0"))
        .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("add metadata from annotations on enumeratum") {
    import sttp.tapir.codec.enumeratum._
    val expectedYaml = load("validator/expected_valid_enumeratum_with_metadata.yml")

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(endpoint.in("numbers").in(jsonBody[Enumeratum.NumberWithMsg]), Info("Numbers", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}

object VerifyYamlEnumeratumTest {
  object Enumeratum {
    import enumeratum.{Enum, EnumEntry}
    import enumeratum.values.{IntEnum, IntEnumEntry}

    case class FruitWithEnum(fruit: String, amount: Int, fruitType: List[FruitType])

    sealed trait FruitType extends EnumEntry

    object FruitType extends Enum[FruitType] {
      case object APPLE extends FruitType
      case object PEAR extends FruitType
      override def values: scala.collection.immutable.IndexedSeq[FruitType] = findValues
    }

    @description("* 1 - One\n* 2 - Two\n* 3 - Three")
    sealed abstract class MyNumber(val value: Int) extends IntEnumEntry

    object MyNumber extends IntEnum[MyNumber] {
      case object One extends MyNumber(1)
      case object Two extends MyNumber(2)
      case object Three extends MyNumber(3)
      override def values = findValues
    }

    case class NumberWithMsg(number: MyNumber, msg: String)
  }
}
