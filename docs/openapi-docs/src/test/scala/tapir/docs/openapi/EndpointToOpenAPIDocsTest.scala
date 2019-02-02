package tapir.docs.openapi

import org.scalatest.{FunSuite, Matchers}
import tapir.Api
import tapir.tests._

class EndpointToOpenAPIDocsTest extends FunSuite with Matchers {
  for (e <- allTestEndpoints) {
    test(s"${e.show} should convert to open api") {
      e.toOpenAPI(Api("title", "19.2-beta-RC1"))
    }
  }

}
