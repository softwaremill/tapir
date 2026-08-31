package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.docs.apispec.DocsExtensionAttribute._
import sttp.tapir.docs.openapi.ReusableComponentAttribute._
import sttp.tapir.json.circe._

class ReusableComponentAttributeTest extends AnyFunSuite with Matchers {

  test("an unmarked input has no marker") {
    query[String]("tenantId").attribute(reusableComponentAttributeKey) shouldBe None
  }

  test("reusableComponent marks an input with no explicit name") {
    query[String]("tenantId").reusableComponent.attribute(reusableComponentAttributeKey) shouldBe Some(ReusableComponent(None))
  }

  test("reusableComponent(name) marks an input with an explicit name") {
    query[String]("tenantId").reusableComponent("TenantId").attribute(reusableComponentAttributeKey) shouldBe Some(
      ReusableComponent(Some("TenantId"))
    )
  }

  test("marking preserves the precise input type and every other property") {
    val q: EndpointInput.Query[String] = query[String]("tenantId").description("The tenant").reusableComponent
    q.name shouldBe "tenantId"
    q.info.description shouldBe Some("The tenant")
  }

  test("the marker works on every markable atom") {
    query[String]("q").reusableComponent.attribute(reusableComponentAttributeKey) shouldBe Some(ReusableComponent(None))
    path[String]("p").reusableComponent.attribute(reusableComponentAttributeKey) shouldBe Some(ReusableComponent(None))
    header[String]("H").reusableComponent.attribute(reusableComponentAttributeKey) shouldBe Some(ReusableComponent(None))
    cookie[String]("c").reusableComponent.attribute(reusableComponentAttributeKey) shouldBe Some(ReusableComponent(None))
    header("H", "v").reusableComponent.attribute(reusableComponentAttributeKey) shouldBe Some(ReusableComponent(None))
  }

  // both attribute objects are meant to be wildcard-imported; an implicit class whose name collides with one in DocsExtensionAttribute
  // would drop out of implicit search, silently breaking docsExtension for anyone importing both
  test("docs extensions still resolve when both attribute objects are imported") {
    query[String]("tenantId").docsExtension("x-a", 1).reusableComponent.attribute(reusableComponentAttributeKey) shouldBe Some(
      ReusableComponent(None)
    )
  }

  test("the marker does not compile on inputs and outputs that cannot become components") {
    assertDoesNotCompile("""stringBody.reusableComponent""")
    assertDoesNotCompile("""statusCode.reusableComponent""")
    assertDoesNotCompile("""queryParams.reusableComponent""")
  }
}
