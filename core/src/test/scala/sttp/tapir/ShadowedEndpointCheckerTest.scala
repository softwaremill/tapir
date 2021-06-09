package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.util.ShadowedEndpointChecker

class ShadowedEndpointCheckerTest extends AnyFlatSpecLike with Matchers {

  it should "detect shadowed endpoints" in {
    val e1 =   endpoint.get.in("x").out(stringBody)
    val e2 =   endpoint.get.in("x").out(stringBody)

    val result = ShadowedEndpointChecker.findShadowedEndpoints(List(e1,e2))

    result.get shouldBe ShadowedEndpoint(e1,e2)
  }

  it should "should return empty result when shadowed endpoints are not present" in {
    val e1 =   endpoint.get.in("x").out(stringBody)
    val e2 =   endpoint.post.in("x").out(stringBody)

    val result = ShadowedEndpointChecker.findShadowedEndpoints(List(e1,e2))

    result shouldBe empty
  }
}
