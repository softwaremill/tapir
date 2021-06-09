package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.util.ShadowedEndpointChecker

class ShadowedEndpointCheckerTest extends AnyFlatSpecLike with Matchers {

  it should "detect shadowed endpoints" in {
    val e1 = endpoint.get.in("y").out(stringBody)
    val e2 = endpoint.get.in("x").out(stringBody)
    val e3 = endpoint.get.in("x/").out(stringBody)
    val e4 = endpoint.get.in("x/*").out(stringBody)
    val e5 = endpoint.get.in("x").out(stringBody)
    val e6 = endpoint.post.in("x").out(stringBody)

    val result = ShadowedEndpointChecker.findShadowedEndpoints(List(e1, e2, e3, e4, e5, e6))

    val expectedResult = List(ShadowedEndpoint(e3, e2), ShadowedEndpoint(e4, e2), ShadowedEndpoint(e5, e2))
    result shouldBe expectedResult
  }

  it should "detect shadowed endpoints with wildcard as first occurrence" in {
    val e1 = endpoint.get.in("a/y").out(stringBody)
    val e2 = endpoint.get.in("a/x/*").out(stringBody)
    val e3 = endpoint.get.in("a/x/").out(stringBody)
    val e4 = endpoint.get.in("a/x").out(stringBody)
    val e5 = endpoint.get.in("a/x/*").out(stringBody)
    val e6 = endpoint.post.in("a/x").out(stringBody)

    val result = ShadowedEndpointChecker.findShadowedEndpoints(List(e1, e2, e3, e4, e5, e6))

    val expectedResult = List(ShadowedEndpoint(e3, e2), ShadowedEndpoint(e4, e2), ShadowedEndpoint(e5, e2))
    result shouldBe expectedResult
  }

  it should "return empty result for empty input" in {
    val result = ShadowedEndpointChecker.findShadowedEndpoints(Nil)
    result shouldBe Nil
  }

  it should "return empty result when no shadowed endpoints exist" in {
    val e1 = endpoint.get.in("a").out(stringBody)
    val e2 = endpoint.get.in("b").out(stringBody)
    val e3 = endpoint.get.in("a/b/*").out(stringBody)

    val result = ShadowedEndpointChecker.findShadowedEndpoints(List(e1, e2, e3))

    result shouldBe Nil
  }
}
