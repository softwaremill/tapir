package sttp.tapir.testing

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

class IncorrectPathsTest extends AnyFlatSpecLike with Matchers {

  it should "not detect incorrect inputs when paths input is not present" in {
    val e = endpoint.get.in("x" / "y")

    val result = EndpointVerifier(List(e))

    result shouldBe Set()
  }

  it should "not detect incorrect inputs when paths input appears as the last input" in {
    val e = endpoint.get.in("x" / paths)

    val result = EndpointVerifier(List(e))

    result shouldBe Set()
  }

  it should "not detect incorrect inputs when paths security input appears as the last security input" in {
    val e = endpoint.get.securityIn("x" / paths)

    val result = EndpointVerifier(List(e))

    result shouldBe Set()
  }

  it should "detect incorrect inputs when paths input appears before the last input" in {
    val e = endpoint.get.in("x" / paths / "y" / "z")

    val result = EndpointVerifier(List(e))

    val expectedResult = Set(IncorrectPathsError(e, 1))
    result shouldBe expectedResult
  }

  it should "detect incorrect inputs when paths security input appears before the last input" in {
    val e = endpoint.get.securityIn(paths).in("x")

    val result = EndpointVerifier(List(e))

    val expectedResult = Set(IncorrectPathsError(e, 0))
    result shouldBe expectedResult
  }
}
