package sttp.tapir.docs.openapi

import enumeratum.EnumEntry.Uppercase
import enumeratum.values.{IntEnum, IntEnumEntry}
import enumeratum.{EnumEntry, _}
import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.docs.openapi.VerifyYamlEnumTest._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.{Schema, Validator, _}

import scala.collection.immutable

class VerifyYamlEnumTest extends AnyFunSuite with Matchers {

  test("should create component for enum using trait") {

    implicit val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(referenceEnums = {
      case soi if soi.fullName.contains("Game") => true
      case _                                    => false
    })

    implicit val schemaForGame: Schema[Game] = Schema.string[Game].validate(Validator.derivedEnum[Game].encode(_.toString.toLowerCase))
    implicit val schemaForEpisode: Schema[Episode] = Schema.string[Episode].validate(Validator.derivedEnum[Episode].encode(_.toString.toLowerCase))

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(Seq(endpoint.in("totalWar").out(jsonBody[TotalWar]), endpoint.in("callOfDuty").out(jsonBody[CallOfDuty])), "Games", "1.0")
      .toYaml

    val expectedYaml = load("enum/expected_trait_enum_component.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should create component for enum using enumeratum Enum") {
    import sttp.tapir.codec.enumeratum._

    implicit val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(referenceEnums = _ => true)

    val actualYaml = OpenAPIDocsInterpreter
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

  test("should create component for enum using enumeratum IntEnum") {
    import sttp.tapir.codec.enumeratum._

    implicit val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(referenceEnums = _ => true)

    val actualYaml = OpenAPIDocsInterpreter
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

object VerifyYamlEnumTest {
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

  sealed abstract class ErrorCode(val value: Int) extends IntEnumEntry

  object ErrorCode extends IntEnum[ErrorCode] {
    case object Error1 extends ErrorCode(1)
    case object Error2 extends ErrorCode(2)

    override def values: immutable.IndexedSeq[ErrorCode] = findValues
  }

  case class Error1Response(error: ErrorCode)
  case class Error2Response(error: ErrorCode)
}
