package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import sttp.tapir.{Schema, Validator}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.circe.yaml._
import io.circe.Codec

class VerifyYamlCoproductTest extends AnyFunSuite with Matchers {
  test("should match expected yaml for coproduct with enum field") {
    implicit val shapeCodec: Codec[Shape] = null
    implicit val schema: Schema[Shape] = Schema
      .oneOfUsingField[Shape, String](
        _.shapeType,
        identity
      )(
        Square.toString() -> implicitly[Schema[Square]]
      )

    val endpoint = sttp.tapir.endpoint.get.out(jsonBody[Shape])

    val expectedYaml = load("expected_coproduct_discriminator_with_enum_circe.yml")
    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(endpoint, "My Bookshop", "1.0").toYaml

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}

object Color extends Enumeration {
  type Color = Value

  val Blue = Value("blue")
  val Red = Value("red")

  implicit def schemaForEnum: Schema[Value] = Schema.string.validate(Validator.enum(values.toList, v => Option(v)))
}

sealed trait Shape {
  def shapeType: String
}
case class Square(color: Color.Value, shapeType: String = "square") extends Shape
