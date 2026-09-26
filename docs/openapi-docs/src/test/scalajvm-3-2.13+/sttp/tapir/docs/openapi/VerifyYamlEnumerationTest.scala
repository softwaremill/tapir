package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.Schema.SName
import sttp.tapir.docs.openapi.VerifyYamlEnumerationTest._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.model.{CommaSeparated, Delimited}
import sttp.tapir.{Schema, Validator, _}

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

  test("should support optional enums and sequences of enums") {
    implicit val shapeCodec: io.circe.Codec[Square] = null
    val e = endpoint.get.out(jsonBody[Square])

    val expectedYaml = load("enum/expected_enum_collections.yml")
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, "Enums", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should support delimited query parameters") {
    val e = endpoint.get.in(query[CommaSeparated[CornerStyle.Value]]("styles"))

    val expectedYaml = load("enum/expected_enum_in_delimited_query.yml")
    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, "Enums", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should support delimited query parameters with a default value") {
    val q = query[CommaSeparated[CornerStyle.Value]]("styles")
      .default(Delimited[",", CornerStyle.Value](List(CornerStyle.Rounded, CornerStyle.Straight)))
      .example(Delimited[",", CornerStyle.Value](List(CornerStyle.Rounded, CornerStyle.Straight)))

    val e = endpoint.get.in(q)

    val expectedYaml = load("enum/expected_enum_in_delimited_query_with_default.yml")
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
