package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OneOfInputTest extends AnyFlatSpec with Matchers {
  private val optionalString = oneOfInput[Option[String]](
    oneOfVariantValueMatcher(emptyInputAs((None: Option[String]))) { case None => true },
    oneOfVariantValueMatcher(stringBody.map(Some(_))(_.get)) { case Some(_) => true }
  )

  "oneOfInput" should "encode None with the empty variant" in {
    val (chosen, low) = optionalString.variantForValue(None)
    chosen shouldBe a[EndpointIO.Empty[_]]
    low shouldBe None
  }

  it should "encode Some with the body variant, unwrapping before the body codec" in {
    val (chosen, low) = optionalString.variantForValue(Some("hello"))
    low shouldBe Some("hello")
    val body = chosen.asInstanceOf[EndpointIO.Body[String, Option[String]]]
    body.codec.encode(Some("hello")) shouldBe "hello"
  }

  it should "reject an output-only variant" in {
    assertThrows[IllegalArgumentException] {
      oneOfInput[String](oneOfVariant(statusCode(sttp.model.StatusCode.Ok).map(_ => "ok")(_ => ())))
    }
  }
}
