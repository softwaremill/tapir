package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.docs.openapi.ReusableComponentAttribute._

class ReusableComponentAttributeTest extends AnyFunSuite with Matchers {

  test("an unmarked input has no marker") {
    query[String]("tenantId").reusableComponentMarker shouldBe None
  }

  test("reusableComponent marks an input with no explicit name") {
    query[String]("tenantId").reusableComponent.reusableComponentMarker shouldBe Some(ReusableComponent(None))
  }

  test("reusableComponent(name) marks an input with an explicit name") {
    query[String]("tenantId").reusableComponent("TenantId").reusableComponentMarker shouldBe Some(
      ReusableComponent(Some("TenantId"))
    )
  }

  test("marking preserves the precise input type and every other property") {
    val q: EndpointInput.Query[String] = query[String]("tenantId").description("The tenant").reusableComponent
    q.name shouldBe "tenantId"
    q.info.description shouldBe Some("The tenant")
  }

  test("the marker works on every markable atom") {
    query[String]("q").reusableComponent.reusableComponentMarker shouldBe Some(ReusableComponent(None))
    path[String]("p").reusableComponent.reusableComponentMarker shouldBe Some(ReusableComponent(None))
    header[String]("H").reusableComponent.reusableComponentMarker shouldBe Some(ReusableComponent(None))
    cookie[String]("c").reusableComponent.reusableComponentMarker shouldBe Some(ReusableComponent(None))
    header("H", "v").reusableComponent.reusableComponentMarker shouldBe Some(ReusableComponent(None))
  }
}
