package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable

import sttp.tapir.Schema
import sttp.tapir.codec.enumeratum._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.circe.yaml._

import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec

import enumeratum._
import enumeratum.EnumEntry._

class VerifyYamlCirceTest extends AnyFunSuite with Matchers {

  test("should match expected yaml for coproduct with enum field") {

    sealed trait Color extends EnumEntry with Hyphencase with Lowercase

    object Color extends Enum[Color] with CirceEnum[Color] {

      val values: immutable.IndexedSeq[Color] = findValues

      case object Red extends Color
      case object Green extends Color
      case object Blue extends Color

    }

    sealed abstract class Shape(final val shapeType: String = "")

    final case class Square(size: Int, color: Color) extends Shape

    final case class Circle(diameter: Int) extends Shape

    implicit val configuration: Configuration = Configuration.default
      .withDiscriminator("shapeType")
      .withKebabCaseConstructorNames

    implicit val codec: Codec.AsObject[Shape] = deriveConfiguredCodec[Shape]

    implicit val schema: Typeclass[Shape] = Schema
      .oneOfUsingField[Shape, String](
        _.shapeType,
        configuration.transformConstructorNames
      )(
        Square.toString() -> implicitly[Schema[Square]],
        Circle.toString() -> implicitly[Schema[Circle]]
      )

    val endpoint = sttp.tapir.endpoint.get.out(jsonBody[Shape])

    val expectedYaml = load("expected_coproduct_discriminator_with_enum_circe.yml")
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint, "My Bookshop", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}
