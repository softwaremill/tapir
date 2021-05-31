package sttp.tapir.docs.openapi

import sttp.tapir.openapi.Info
import sttp.tapir.tests._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EndpointToOpenAPIDocsTest extends AnyFunSuite with Matchers {
  for (e <- allTestEndpoints) {
    test(s"${e.showDetail} should convert to open api") {
      OpenAPIDocsInterpreter.toOpenAPI(e, Info("title", "19.2-beta-RC1"))
    }
  }
}
