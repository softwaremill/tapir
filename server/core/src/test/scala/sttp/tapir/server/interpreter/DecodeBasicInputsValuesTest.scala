package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

class DecodeBasicInputsValuesTest extends AnyFlatSpec with Matchers {
  private def emptyValues(size: Int) =
    DecodeBasicInputsResult.Values(Vector.fill[Any](size)(null), None)

  it should "record an extracted body separately from the primary body" in {
    val result = emptyValues(1).addBodyInput(extractBodyFromRequest(stringBody), 0)

    result.bodyInputWithIndex shouldBe None
    result.extractedBodyInputsWithIndex.map(_._2) shouldBe Vector(0)
    result.hasExtractedBody shouldBe true
  }

  it should "record a primary body in bodyInputWithIndex" in {
    val result = emptyValues(1).addBodyInput(stringBody, 0)

    result.bodyInputWithIndex shouldBe defined
    result.extractedBodyInputsWithIndex shouldBe empty
    result.hasExtractedBody shouldBe false
  }

  it should "allow a primary body alongside several extracted bodies" in {
    val result = emptyValues(3)
      .addBodyInput(extractBodyFromRequest(stringBody), 0)
      .addBodyInput(stringBody, 1)
      .addBodyInput(extractBodyFromRequest(byteArrayBody), 2)

    result.bodyInputWithIndex.map(_._2) shouldBe Some(1)
    result.extractedBodyInputsWithIndex.map(_._2) shouldBe Vector(0, 2)
  }

  it should "still reject two primary bodies in one pass" in {
    an[IllegalStateException] should be thrownBy {
      emptyValues(2).addBodyInput(stringBody, 0).addBodyInput(stringBody, 1)
    }
  }

  it should "report no extracted body for a decode failure" in {
    val failure: DecodeBasicInputsResult =
      DecodeBasicInputsResult.Failure(stringBody, DecodeResult.Missing)
    failure.hasExtractedBody shouldBe false
  }
}
