package sttp.tapir.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OpenAPITest extends AnyFunSuite with Matchers {
  test("'default' in ServerVariable should belong to 'enum'") {
    a[java.lang.IllegalArgumentException] shouldBe thrownBy {
      Server("https://{username}.example.com:{port}/{basePath}")
        .description("The production API server")
        .variables(
          "username" -> ServerVariable(None, "demo", Some("Username")),
          "port" -> ServerVariable(Some(List("8443", "443")), "80", None),
          "basePath" -> ServerVariable(None, "v2", None)
        )
    }
  }
}
