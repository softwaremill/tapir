package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.shared.Identity
import sttp.tapir._

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
}
