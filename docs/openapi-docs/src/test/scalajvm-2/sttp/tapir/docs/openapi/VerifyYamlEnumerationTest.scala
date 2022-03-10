package sttp.tapir.docs.openapi

import enumeratum.EnumEntry.Uppercase
import enumeratum.values.{IntEnum, IntEnumEntry}
import enumeratum.{EnumEntry, _}
import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.docs.openapi.VerifyYamlEnumerationTest._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.{Schema, Validator, _}

import scala.collection.immutable

class VerifyYamlEnumerationTest extends AnyFunSuite with Matchers {

  test("should create component for enum using trait") {
    implicit val schemaForGame: Schema[Game] = Schema.derivedEnumeration[Game](encode = Some(_.toString.toLowerCase))
    implicit val schemaForEpisode: Schema[Episode] =
      Schema.derivedEnumeration[Episode](encode = Some(_.toString.toLowerCase)).copy(name = None)

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(Seq(endpoint.in("totalWar").out(jsonBody[TotalWar]), endpoint.in("callOfDuty").out(jsonBody[CallOfDuty])), "Games", "1.0")
      .toYaml

    val expectedYaml = load("enum/expected_trait_enum_component.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should create component for enum using enumeratum Enum") {
    import sttp.tapir.codec.enumeratum._

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
    import sttp.tapir.codec.enumeratum._

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
    import sttp.tapir.codec.enumeratum._

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

  test("should support optional enums and sequences of enums") {
    implicit val shapeCodec: io.circe.Codec[Square] = null
    val e = endpoint.get.out(jsonBody[Square])

    val expectedYaml = load("enum/expected_enum_collections.yml")
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, "Enums", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}

object VerifyYamlEnumerationTest {
  sealed trait Game
  case object Strategy extends Game
  case object Action extends Game

  sealed trait Episode
  case object First extends Episode
  case object Second extends Episode

  case class TotalWar(game: Game, episode: Episode)
  case class CallOfDuty(game: Game, episode: Episode)

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

  sealed abstract class ErrorCode(val value: Int) extends IntEnumEntry

  object ErrorCode extends IntEnum[ErrorCode] {
    case object Error1 extends ErrorCode(1)
    case object Error2 extends ErrorCode(2)

    override def values: immutable.IndexedSeq[ErrorCode] = findValues
  }

  case class Error1Response(error: ErrorCode)
  case class Error2Response(error: ErrorCode)

  object CornerStyle extends Enumeration {
    type CornerStyle = Value

    val Rounded = Value("rounded")
    val Straight = Value("straight")

    implicit def schemaForEnum: Schema[Value] =
      Schema.string.validate(Validator.enumeration(values.toList, v => Option(v), Some(SName("CornerStyle"))))
  }

  object Tag extends Enumeration {
    type Tag = Value

    val Tag1 = Value("tag1")
    val Tag2 = Value("tag2")

    implicit def schemaForEnum: Schema[Value] =
      Schema.string.validate(Validator.enumeration(values.toList, v => Option(v), Some(SName("Tag"))))
  }

  case class Square(cornerStyle: Option[CornerStyle.Value], tags: Seq[Tag.Value])
}
