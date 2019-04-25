package tapir.docs.openapi

import org.scalatest.{FunSuite, Matchers}
import tapir.openapi.Info
import tapir.tests._

class EndpointToOpenAPIDocsTest extends FunSuite with Matchers {
  for (e <- allTestEndpoints) {
    test(s"${e.showDetail} should convert to open api") {
      e.toOpenAPI(Info("title", "19.2-beta-RC1"))
    }
  }

}
