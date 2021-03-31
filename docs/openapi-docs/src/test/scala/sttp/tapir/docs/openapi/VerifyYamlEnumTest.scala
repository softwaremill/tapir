package sttp.tapir.docs.openapi

import enumeratum.EnumEntry.Uppercase
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
    implicit val schemaForGame: Schema[Game] = Schema.string[Game].validate(Validator.derivedEnum[Game].encode(_.toString.toLowerCase))

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(Seq(endpoint.in("totalWar").out(jsonBody[TotalWar]), endpoint.in("callOfDuty").out(jsonBody[CallOfDuty])), "Games", "1.0")
      .toYaml

    val expectedYaml = load("enum/expected_trait_enum_component.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should create component for enum using enumeratum") {
    import sttp.tapir.codec.enumeratum._

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
}

object VerifyYamlEnumTest {
  implicit val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(useRefForEnums = true)

  sealed trait Game
  case object Strategy extends Game
  case object Action extends Game

  case class TotalWar(game: Game)
  case class CallOfDuty(game: Game)

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
}
