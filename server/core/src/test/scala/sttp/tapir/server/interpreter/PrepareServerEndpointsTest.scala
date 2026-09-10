package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.server.TestUtil

class PrepareServerEndpointsTest extends AnyFlatSpec with Matchers {
  it should "throw when an endpoint declares two primary bodies" in {
    val se = endpoint.post
      .in("people")
      .securityIn(stringBody)
      .in(stringBody)
      .serverSecurityLogic[Unit, Identity](_ => Right(()))
      .serverLogic(_ => _ => Right(()))

    val e = the[IllegalArgumentException] thrownBy PrepareServerEndpoints(List(se))
    e.getMessage should include("asSecondary")
  }

  it should "accept an endpoint with a secondary body" in {
    val se = endpoint.post
      .in("people")
      .securityIn(stringBody.asSecondary)
      .in(stringBody)
      .serverSecurityLogic[Unit, Identity](_ => Right(()))
      .serverLogic(_ => _ => Right(()))

    noException should be thrownBy PrepareServerEndpoints(List(se))
  }

  it should "return a filter which matches endpoints by path" in {
    val se = endpoint.get
      .in("people")
      .serverSecurityLogic[Unit, Identity](_ => Right(()))
      .serverLogic(_ => _ => Right(()))

    val filter = PrepareServerEndpoints(List(se))

    filter(TestUtil.createTestRequest(List("people"))) shouldBe List(se)
    filter(TestUtil.createTestRequest(List("other"))) shouldBe Nil
  }
}
