package sttp.tapir.docs.openapi

import io.circe.Printer
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.openapi._
import sttp.tapir.openapi.circe._
import sttp.tapir.tests.Basic.{delete_endpoint, in_query_query_out_string}

class VerifyJsonTest extends AnyFunSuite with Matchers {

  test("should match the expected json") {
    val expectedJson = load("expected.json")

    val actualJson = OpenAPIDocsInterpreter().toOpenAPI(List(in_query_query_out_string, delete_endpoint), Info("Fruits", "1.0")).asJson
    val actualJsonNoIndent = noIndentation(Printer.spaces2.print(actualJson))

    actualJsonNoIndent shouldBe expectedJson
  }
}
