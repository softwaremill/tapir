package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.tapir._
import sttp.tapir.docs.apispec.nameAllPathCapturesInEndpoint
import sttp.tapir.docs.apispec.schema.SchemasForEndpoints
import sttp.tapir.internal._

class EndpointToParametersTest extends AnyFunSuite with Matchers {

  test("should produce exactly the Parameter values that end up in the operation") {
    val e = endpoint.get
      .in("books" / path[String]("genre"))
      .in(query[Int]("limit").description("Maximum number of books"))
      .in(header[String]("X-Auth-Token"))
      .in(cookie[String]("session"))

    // what the interpreter actually emits
    val emitted = OpenAPIDocsInterpreter()
      .toOpenAPI(e, Info("test", "1.0"))
      .paths
      .pathItems
      .values
      .head
      .get // PathItem.get: Option[Operation]
      .get // unwrap the Option
      .parameters
      .collect { case Right(p) => p }

    // what EndpointToParameters computes, set up exactly as EndpointToOpenAPIDocs.toOpenAPI does
    val options = OpenAPIDocsOptions.default
    val es = List(e).filter(e2 => findWebSocket(e2).isEmpty).map(nameAllPathCapturesInEndpoint)
    val additionalOutputs = es.flatMap(e2 => options.defaultDecodeFailureOutput(e2.input)).toSet.toList
    val (_, tschemaToASchema) =
      new SchemasForEndpoints(es, options.schemaName, options.markOptionsAsNullable, options.failOnDuplicateSchemaName, additionalOutputs)
        .apply()

    val endpointToParameters = new EndpointToParameters(tschemaToASchema)
    val recomputed = es.flatMap { e2 =>
      endpointToParameters(endpointToParameters.filterOutHiddenInputs(e2.asVectorOfBasicInputs(includeAuth = false)))
    }.distinct

    recomputed shouldBe emitted
  }

  test("withSourceAtoms pairs each parameter with the atom it came from") {
    val q = query[Int]("limit")
    val h = header[String]("X-Auth-Token")
    val e = endpoint.get.in("books").in(q).in(h)

    val options = OpenAPIDocsOptions.default
    val es = List(nameAllPathCapturesInEndpoint(e))
    val (_, tschemaToASchema) =
      new SchemasForEndpoints(es, options.schemaName, options.markOptionsAsNullable, options.failOnDuplicateSchemaName, Nil).apply()

    val endpointToParameters = new EndpointToParameters(tschemaToASchema)
    val pairs = endpointToParameters.withSourceAtoms(
      endpointToParameters.filterOutHiddenInputs(es.head.asVectorOfBasicInputs(includeAuth = false))
    )

    pairs.map(_._1) shouldBe Vector(q, h)
    pairs.map(_._2.name) shouldBe Vector("limit", "X-Auth-Token")
  }

  test("hidden inputs are filtered out before conversion") {
    val e = endpoint.get
      .in("books")
      .in(query[Int]("limit"))
      .in(query[String]("secret").schema(_.hidden(true)))

    val options = OpenAPIDocsOptions.default
    val es = List(nameAllPathCapturesInEndpoint(e))
    val (_, tschemaToASchema) =
      new SchemasForEndpoints(es, options.schemaName, options.markOptionsAsNullable, options.failOnDuplicateSchemaName, Nil).apply()

    val endpointToParameters = new EndpointToParameters(tschemaToASchema)
    val parameters = endpointToParameters(
      endpointToParameters.filterOutHiddenInputs(es.head.asVectorOfBasicInputs(includeAuth = false))
    )

    parameters.map(_.name) shouldBe Vector("limit")
  }
}
