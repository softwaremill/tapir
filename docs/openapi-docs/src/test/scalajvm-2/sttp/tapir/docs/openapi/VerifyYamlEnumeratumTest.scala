package sttp.tapir.docs.openapi

import enumeratum.EnumEntry.Uppercase
import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.Schema.annotations.{default, description}
import sttp.tapir._
import sttp.tapir.docs.openapi.VerifyYamlEnumeratumTest._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import scala.collection.immutable

class VerifyYamlEnumeratumTest extends AnyFunSuite with Matchers {
  import sttp.tapir.codec.enumeratum._

  test("should use enumeratum validator for array elements") {
    val expectedYaml = load("validator/expected_valid_enumeratum.yml")

    val actualYaml =
      OpenAPIDocsInterpreter()
        .toOpenAPI(List(endpoint.in("enum-test").out(jsonBody[FruitWithEnum])), Info("Fruits", "1.0"))
        .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should add metadata from annotations on enumeratum") {
    val expectedYaml = load("validator/expected_valid_enumeratum_with_metadata.yml")

    val actualYaml =
      OpenAPIDocsInterpreter()
        .toOpenAPI(endpoint.in("numbers").in(jsonBody[NumberWithMsg]), Info("Numbers", "1.0"))
        .toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should add enum default") {
    val expectedYaml = load("enum/expected_enumeratum_enum_default.yml")
    val ep = endpoint
      .in(query[FruitType]("type").example(FruitType.APPLE).default(FruitType.PEAR))
      .out(jsonBody[FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(ep, Info("Fruits", "1.0")).toYaml

    // TODO fix test

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should use different default values") {
    val expectedYaml = load("enum/expected_enumeratum_enum_using_first_specified_default_value.yml")
    val ep1 = endpoint
      .in("fruit-by-type1")
      .in(query[FruitType]("type1").default(FruitType.PEAR))
      .out(jsonBody[FruitWithEnum])
    val ep2 = endpoint
      .in("fruit-by-type2")
      .in(query[FruitType]("type2").default(FruitType.APPLE))
      .out(jsonBody[FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(List(ep1, ep2), Info("Fruits", "1.0")).toYaml

    // TODO fix test

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should not add default when no encoded value specified") {
    val expectedYaml = load("enum/expected_enumeratum_enum_not_adding_default_when_no_encoded_value_specified.yml")
    val ep = endpoint.post
      .in(jsonBody[FruitQuery])
      .out(jsonBody[FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(ep, Info("Fruits", "1.0")).toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  // #1800
  test("should add default when encoded value specified") {
    val expectedYaml = load("enum/expected_enumeratum_enum_adding_default_when_encoded_value_specified.yml")
    val ep = endpoint.post
      .in(jsonBody[FruitQueryWithEncoded])
      .out(jsonBody[FruitWithEnum])

    val actualYaml =
      OpenAPIDocsInterpreter().toOpenAPI(ep, Info("Fruits", "1.0")).toYaml

    // TODO fix test

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should create component for enum using enumeratum Enum") {
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        Seq(
          endpoint.in("poland").out(jsonBody[Poland]),
          endpoint.in("belgium").out(jsonBody[Belgium]),
          endpoint.in("luxembourg").out(jsonBody[Luxembourg])
        ),
        "Countries",
        "1.0"
      )
      .toYaml
    val expectedYaml = load("enum/expected_enumeratum_enum_component.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should create component for optional and collections of enums") {
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        Seq(
          endpoint.in("countryCollection").out(jsonBody[CountryCollection])
        ),
        "Countries",
        "1.0"
      )
      .toYaml
    val expectedYaml = load("enum/expected_enumeratum_enum_collection_component.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should create component for enum using enumeratum IntEnum") {
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        Seq(endpoint.in("error1").out(jsonBody[Error1Response]), endpoint.in("error2").out(jsonBody[Error2Response])),
        "Errors",
        "1.0"
      )
      .toYaml

    val expectedYaml = load("enum/expected_enumeratum_int_enum_component.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}

object VerifyYamlEnumeratumTest {
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
    override def values: immutable.IndexedSeq[MyNumber] = findValues
  }

  case class NumberWithMsg(number: MyNumber, msg: String)

  sealed abstract class CountryCode extends EnumEntry

  object CountryCode extends Enum[CountryCode] {
    case object PL extends CountryCode with Uppercase
    case object BE extends CountryCode with Uppercase
    case object LU extends CountryCode with Uppercase

    override def values: immutable.IndexedSeq[CountryCode] = findValues
  }

  case class Poland(countryCode: CountryCode)
  case class Belgium(countryCode: CountryCode)
  case class Luxembourg(countryCode: CountryCode)

  case class CountryCollection(countryCode: CountryCode, countryCodeOpt: Option[CountryCode], countryCodeMulti: List[CountryCode])

  case class Error1Response(error: ErrorCode)
  case class Error2Response(error: ErrorCode)

  sealed abstract class ErrorCode(val value: Int) extends IntEnumEntry

  object ErrorCode extends IntEnum[ErrorCode] {
    case object Error1 extends ErrorCode(1)
    case object Error2 extends ErrorCode(2)

    override def values: immutable.IndexedSeq[ErrorCode] = findValues
  }
}
