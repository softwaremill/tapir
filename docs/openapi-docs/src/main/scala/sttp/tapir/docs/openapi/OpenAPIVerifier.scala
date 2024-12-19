package sttp.tapir.docs.openapi

import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.validation._
import sttp.tapir._
import io.circe._
import io.circe.yaml.parser
import sttp.apispec.openapi.circe.openAPIDecoder

sealed trait Mode
object Mode {
  case object Exact extends Mode
  case object AtLeast extends Mode
  case object AtMost extends Mode
}

object OpenAPIVerifier {
  def apply(endpoints: List[AnyEndpoint], yamlOpenApiSpec: String, mode: Mode): OpenAPIVerifier =
    new OpenAPIVerifier(endpoints, yamlOpenApiSpec, mode)
}

/** A utility for verifying the compatibility of Tapir endpoints with an OpenAPI specification.
  *
  * The `OpenAPIVerifier` class checks whether a given set of Tapir endpoints matches an OpenAPI specification provided as a YAML string. It
  * supports three verification modes:
  *   - `Exact`: Every Tapir endpoint corresponds to exactly one OpenAPI endpoint, and vice versa.
  *   - `AtLeast`: All Tapir endpoints must exist in the OpenAPI specification, but the specification may contain additional endpoints.
  *   - `AtMost`: All OpenAPI endpoints must exist in the Tapir endpoints, but there may be additional Tapir endpoints.
  *
  * This class leverages the OpenAPIComparator to compare Tapir-generated OpenAPI specifications with the provided OpenAPI specification.
  *
  * @param endpoints
  *   the list of Tapir endpoints to verify.
  * @param yamlOpenApiSpec
  *   the OpenAPI specification in YAML format, provided as a string.
  * @param mode
  *   the verification mode (`Exact`, `AtLeast`, or `AtMost`) to use for the comparison.
  */

class OpenAPIVerifier private (endpoints: List[AnyEndpoint], yamlOpenApiSpec: String, mode: Mode) {

  /** Verifies the compatibility of the Tapir endpoints with the provided OpenAPI specification.
    *
    * This method uses the specified verification mode to compare the Tapir endpoints with the OpenAPI specification. It ensures that the
    * endpoints are compatible according to the rules of the chosen mode, detecting issues such as missing endpoints, parameter mismatches,
    * and schema inconsistencies.
    *
    * @return
    *   a list of `OpenAPICompatibilityIssue` instances detailing the compatibility issues found during verification.
    *   - An empty list indicates that the endpoints are fully compatible with the OpenAPI specification.
    *   - A non-empty list indicates detected compatibility issues.
    */

  def verify(): List[OpenAPICompatibilityIssue] = {
    val tapirOpenApi = OpenAPIDocsInterpreter().toOpenAPI(endpoints, "OpenAPIVerifier", "1.0")
    val openApi = readOpenAPIFromString(yamlOpenApiSpec)

    mode match {
      case Mode.AtMost =>
        val openAPIComparator = OpenAPIComparator(tapirOpenApi, openApi)
        openAPIComparator.compare()

      case Mode.AtLeast =>
        val openAPIComparator = OpenAPIComparator(openApi, tapirOpenApi)
        openAPIComparator.compare()

      case Mode.Exact =>
        val openAPIComparator = OpenAPIComparator(tapirOpenApi, openApi)
        val openAPIComparatorA = OpenAPIComparator(openApi, tapirOpenApi)
        openAPIComparator.compare() ++ openAPIComparatorA.compare()
    }
  }

  private def readOpenAPIFromString(yamlOpenApiSpec: String): OpenAPI = {
    parser.parse(yamlOpenApiSpec).flatMap(_.as[OpenAPI]) match {
      case Right(openapi) => openapi
      case _              => throw new IllegalArgumentException("Failed to parse OpenAPI YAML specification")
    }
  }
}
