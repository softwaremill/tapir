package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.Schema.annotations.{default, description}
import sttp.tapir._
import sttp.tapir.docs.openapi.VerifyYamlEnumeratumTest.Enumeratum
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class VerifyYamlEnumeratumTest extends AnyFunSuite with Matchers {
  test("should use enumeratum validator for array elements") {
    import sttp.tapir.codec.enumeratum._

    val expectedYaml = load("validator/expected_valid_enumeratum.yml")

    val actualYaml =
      OpenAPIDocsInterpreter()
        .toOpenAPI(List(endpoint.in("enum-test").out(jsonBody[Enumeratum.FruitWithEnum])), Info("Fruits", "1.0"))
        .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should add metadata from annotations on enumeratum") {
    import sttp.tapir.codec.enumeratum._
    val expectedYaml = load("validator/expected_valid_enumeratum_with_metadata.yml")

    val actualYaml =
      OpenAPIDocsInterpreter()
        .toOpenAPI(endpoint.in("numbers").in(jsonBody[Enumeratum.NumberWithMsg]), Info("Numbers", "1.0"))
        .toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should add enum default") {
    import sttp.tapir.codec.enumeratum._
    import sttp.tapir.docs.openapi.VerifyYamlEnumeratumTest.Enumeratum.FruitType._

    val expectedYaml = load("enum/expected_enumeratum_enum_default.yml")
    val ep = endpoint
      .in(query[Enumeratum.FruitType]("type").example(APPLE).default(PEAR))
      .out(jsonBody[Enumeratum.FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(ep, Info("Fruits", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should use first specified default value") {
    import sttp.tapir.codec.enumeratum._
    import sttp.tapir.docs.openapi.VerifyYamlEnumeratumTest.Enumeratum.FruitType._

    val expectedYaml = load("enum/expected_enumeratum_enum_using_first_specified_default_value.yml")
    val ep1 = endpoint
      .in("fruit-by-type1")
      .in(query[Enumeratum.FruitType]("type1").default(PEAR))
      .out(jsonBody[Enumeratum.FruitWithEnum])
    val ep2 = endpoint
      .in("fruit-by-type2")
      .in(query[Enumeratum.FruitType]("type2").default(APPLE))
      .out(jsonBody[Enumeratum.FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(List(ep1, ep2), Info("Fruits", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should not add default when no encoded value specified") {
    import sttp.tapir.codec.enumeratum._

    val expectedYaml = load("enum/expected_enumeratum_enum_not_adding_default_when_no_encoded_value_specified.yml")
    val ep = endpoint.post
      .in(jsonBody[Enumeratum.FruitQuery])
      .out(jsonBody[Enumeratum.FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(ep, Info("Fruits", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should add default when encoded value specified") {
    import sttp.tapir.codec.enumeratum._

    val expectedYaml = load("enum/expected_enumeratum_enum_adding_default_when_encoded_value_specified.yml")
    val ep = endpoint.post
      .in(jsonBody[Enumeratum.FruitQueryWithEncoded])
      .out(jsonBody[Enumeratum.FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(ep, Info("Fruits", "1.0")).toYaml

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

    case class FruitQuery(@default(FruitType.PEAR) fruitType: FruitType)
    case class FruitQueryWithEncoded(@default(FruitType.PEAR, encoded = Some(FruitType.PEAR)) fruitType: FruitType)

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
