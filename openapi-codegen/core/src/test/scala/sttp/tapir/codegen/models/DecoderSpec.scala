package sttp.tapir.codegen.openapi.models

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.codegen.openapi.models.OpenapiModels.{Ref, ResolvableDecoder, Resolved}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.typeAndNullable

class DecoderSpec extends AnyFlatSpec with Matchers {
  "typeAndNullable" should "accept a single type and the nullable flag" in {
    typeAndNullable(Json.obj("type" -> Json.fromString("string")).hcursor) shouldBe Right("string" -> false)
    typeAndNullable(Json.obj("type" -> Json.fromString("string"), "nullable" -> Json.True).hcursor) shouldBe Right("string" -> true)
  }

  it should "accept either ordering of a nullable type list" in {
    Seq(Seq("string", "null"), Seq("null", "string")).foreach { types =>
      typeAndNullable(Json.obj("type" -> Json.arr(types.map(Json.fromString)*)).hcursor) shouldBe Right("string" -> true)
    }
  }

  it should "return a decoding failure for unsupported type lists" in {
    Seq(Seq.empty, Seq("string", "integer"), Seq("string", "integer", "null")).foreach { types =>
      val result = typeAndNullable(Json.obj("type" -> Json.arr(types.map(Json.fromString)*)).hcursor)
      result.left.map(_.message) shouldBe Left("Type lists are only supported for lists of length two where one type is 'null'")
    }
  }

  "ResolvableDecoder" should "decode inline values before falling back to a reference" in {
    ResolvableDecoder[String].decodeJson(Json.fromString("inline")) shouldBe Right(Resolved("inline"))
    ResolvableDecoder[String].decodeJson(Json.obj("$ref" -> Json.fromString("#/components/schemas/Thing"))) shouldBe Right(
      Ref[String]("#/components/schemas/Thing")
    )
  }
}
