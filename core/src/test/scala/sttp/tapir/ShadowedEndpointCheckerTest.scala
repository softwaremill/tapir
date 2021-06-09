package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.util.ShadowedEndpointChecker

class ShadowedEndpointCheckerTest extends AnyFlatSpecLike with Matchers {

  it should "should detect shadowed endpoints" in {
    val e1 =   endpoint.get.in("x").out(stringBody)
    val e2 =   endpoint.get.in("x").out(stringBody)

    val result = ShadowedEndpointChecker.findShadowedEndpoints(List(e1,e2))

    result.get shouldBe ShadowedEndpoint(e1,e2)
  }
}
