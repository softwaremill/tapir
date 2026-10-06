package sttp.tapir.server.armeria

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._

import scala.concurrent.Future

class ArmeriaEndpointVerificationTest extends AnyFunSuite with Matchers {

  test("reject an invalid endpoint when the service is created") {
    val se = endpoint.post
      .in("people")
      .securityIn(stringBody)
      .in(stringBody)
      .serverSecurityLogic[Unit, Future](_ => Future.successful(Right(())))
      .serverLogic(_ => _ => Future.successful(Right(())))

    val e = the[IllegalArgumentException] thrownBy ArmeriaFutureServerInterpreter().toService(se)
    e.getMessage should include("asSecondary")
  }
}
