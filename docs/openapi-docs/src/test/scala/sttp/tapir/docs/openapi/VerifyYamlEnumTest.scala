package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.docs.openapi.VerifyYamlEnumTest._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.{Schema, Validator, _}

class VerifyYamlEnumTest extends AnyFunSuite with Matchers {

  test("should create component for enum") {
    implicit val schemaForGame: Schema[Game] = Schema.derived.validate(Validator.enum.encode(_.toString.toLowerCase))

    implicit val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(enumsToComponents = true)

    val actualYaml = OpenAPIDocsInterpreter
      .toOpenAPI(Seq(endpoint.in("totalWar").out(jsonBody[TotalWar]), endpoint.in("callOfDuty").out(jsonBody[CallOfDuty])), "Games", "1.0")
      .toYaml

    val expectedYaml = load("enum/expected_enum_component.yml")

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }
}

object VerifyYamlEnumTest {
  sealed trait Game
  case object Strategy extends Game
  case object Action extends Game

  case class TotalWar(game: Game)
  case class CallOfDuty(game: Game)
}
