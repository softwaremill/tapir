package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

class DecodeBasicInputsValuesTest extends AnyFlatSpec with Matchers {
  private def emptyValues(size: Int) =
    DecodeBasicInputsResult.Values(Vector.fill[Any](size)(null), None)

  it should "allow a primary body alongside several secondary bodies" in {
    val result = emptyValues(3)
      .addBodyInput(stringBody.asSecondary, 0)
      .addBodyInput(stringBody, 1)
      .addBodyInput(byteArrayBody.asSecondary, 2)

    result.bodyInputWithIndex.map(_._2) shouldBe Some(1)
    result.secondaryBodyInputsWithIndex.map(_._2) shouldBe Vector(0, 2)
  }

  it should "still reject two primary bodies in one pass" in {
    an[IllegalStateException] should be thrownBy {
      emptyValues(2).addBodyInput(stringBody, 0).addBodyInput(stringBody, 1)
    }
  }
}
