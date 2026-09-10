package sttp.tapir.docs.openapi

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir._

class SecondaryBodyDocsTest extends AnyFlatSpec with Matchers {
  it should "document only the primary body" in {
    val e = endpoint.post
      .in("people")
      .securityIn(stringBody.asSecondary)
      .in(byteArrayBody)

    // the default 400 is always documented as text/plain; suppressing it isolates the request body under test
    val options = OpenAPIDocsOptions.default.copy(defaultDecodeFailureOutput = _ => None)
    val yaml = OpenAPIDocsInterpreter(options).toOpenAPI(e, "Test", "1.0").toYaml

    yaml should include("application/octet-stream")
    yaml should not include ("text/plain")
  }

  it should "document no body when the only body is secondary" in {
    val e = endpoint.post.in("ingest").securityIn(stringBody.asSecondary).out(stringBody)

    val yaml = OpenAPIDocsInterpreter().toOpenAPI(e, "Test", "1.0").toYaml

    yaml should not include ("requestBody")
  }
}
