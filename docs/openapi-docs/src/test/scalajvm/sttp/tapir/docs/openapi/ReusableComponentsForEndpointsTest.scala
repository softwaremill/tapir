package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.docs.apispec.nameAllPathCapturesInEndpoint
import sttp.tapir.docs.apispec.schema.SchemasForEndpoints
import sttp.tapir.docs.openapi.ReusableComponentAttribute._

class ReusableComponentsForEndpointsTest extends AnyFunSuite with Matchers {

  private def prePass(es: List[AnyEndpoint], failOnDuplicateComponentName: Boolean = true): ReusableComponents = {
    val options = OpenAPIDocsOptions.default
    val named = es.map(nameAllPathCapturesInEndpoint)
    val (_, tschemaToASchema) =
      new SchemasForEndpoints(named, options.schemaName, options.markOptionsAsNullable, options.failOnDuplicateSchemaName, Nil).apply()
    new ReusableComponentsForEndpoints(named, tschemaToASchema, failOnDuplicateComponentName).apply()
  }

  test("collects nothing when nothing is marked") {
    prePass(List(endpoint.get.in("books").in(query[String]("tenantId")))) shouldBe ReusableComponents.empty
  }

  test("names a marked parameter after the parameter itself") {
    val tenantId = query[String]("tenantId").reusableComponent
    val result = prePass(List(endpoint.get.in("books").in(tenantId)))

    result.parameterToName.values.toList shouldBe List("tenantId")
    result.headerToName shouldBe empty
  }

  test("an explicit marker name wins over the parameter's name") {
    val tenantId = query[String]("tenantId").reusableComponent("TenantId")
    prePass(List(endpoint.get.in("books").in(tenantId))).parameterToName.values.toList shouldBe List("TenantId")
  }

  test("the same marked val used by several endpoints yields one component") {
    val tenantId = query[String]("tenantId").reusableComponent
    val result = prePass(
      List(
        endpoint.get.in("books").in(tenantId),
        endpoint.get.in("magazines").in(tenantId),
        endpoint.get.in("papers").in(tenantId)
      )
    )

    result.parameterToName should have size 1
    result.parameterToName.values.toList shouldBe List("tenantId")
  }

  test("a marked parameter used by exactly one endpoint is still hoisted") {
    val tenantId = query[String]("tenantId").reusableComponent
    prePass(List(endpoint.get.in("books").in(tenantId))).parameterToName should have size 1
  }

  test("a marked but hidden parameter yields no component") {
    val secret = query[String]("secret").schema(_.hidden(true)).reusableComponent
    prePass(List(endpoint.get.in("books").in(secret))) shouldBe ReusableComponents.empty
  }

  test("a marked request header is collected as a parameter") {
    val requestId = header[String]("X-Request-Id").reusableComponent
    val result = prePass(List(endpoint.get.in("books").in(requestId)))

    result.parameterToName should have size 1
    result.parameterToName.keys.head.in.value shouldBe "header"
  }

  test("marking one use site also references structurally identical unmarked ones") {
    val marked = query[String]("tenantId").description("The tenant").reusableComponent
    val unmarked = query[String]("tenantId").description("The tenant")
    val result = prePass(List(endpoint.get.in("books").in(marked), endpoint.get.in("magazines").in(unmarked)))

    result.parameterToName should have size 1
  }

  test("two different parameters claiming one name fail by default") {
    val a = query[String]("tenantId").description("A").reusableComponent
    val b = query[Int]("tenantId").description("B").reusableComponent

    val thrown = intercept[IllegalStateException] {
      prePass(List(endpoint.get.in("books").in(a), endpoint.get.in("magazines").in(b)))
    }
    thrown.getMessage should include("tenantId")
    thrown.getMessage should include("reusableComponent")
  }

  test("two different parameters claiming one name are suffixed when the check is off") {
    val a = query[String]("tenantId").description("A").reusableComponent
    val b = query[Int]("tenantId").description("B").reusableComponent

    val result = prePass(
      List(endpoint.get.in("books").in(a), endpoint.get.in("magazines").in(b)),
      failOnDuplicateComponentName = false
    )

    result.parameterToName.values.toList.sorted shouldBe List("tenantId", "tenantId1")
  }
}
