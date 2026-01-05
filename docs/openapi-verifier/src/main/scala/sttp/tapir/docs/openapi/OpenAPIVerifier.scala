package sttp.tapir.docs.openapi

import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.validation._
import sttp.tapir._
import io.circe._
import io.circe.yaml.parser
import sttp.apispec.openapi.circe.openAPIDecoder

/** A utility for verifying the compatibility of Tapir endpoints with an OpenAPI specification.
  *
  * The `OpenAPIVerifier` object provides methods to verify compatibility between endpoints and OpenAPI specifications, or client endpoints
  * and server OpenAPI specifications. The compatibility check detects issues such as missing endpoints, parameter mismatches, and schema
  * inconsistencies.
  */
object OpenAPIVerifier {

  /** Verifies that the provided client endpoints are compatible with the given server OpenAPI specification.
    *
    * @param clientEndpoints
    *   the list of client Tapir endpoints to verify.
    * @param serverSpecificationYaml
    *   the OpenAPI specification provided by the server, in YAML format.
    * @return
    *   a list of `OpenAPICompatibilityIssue` instances detailing the compatibility issues found during verification, or `Nil` if no issues
    *   were found.
    */
  def verifyClient(clientEndpoints: List[AnyEndpoint], serverSpecificationYaml: String): List[OpenAPICompatibilityIssue] = {
    val clientOpenAPI = OpenAPIDocsInterpreter().toOpenAPI(clientEndpoints, "OpenAPIVerifier", "1.0")
    val serverOpenAPI = readOpenAPIFromString(serverSpecificationYaml)

    OpenAPIComparator(clientOpenAPI, serverOpenAPI).compare()
  }

  /** Verifies that the client OpenAPI specification is compatible with the provided server endpoints.
    *
    * @param serverEndpoints
    *   the list of server Tapir endpoints to verify.
    * @param clientSpecificationYaml
    *   the OpenAPI specification provided by the client, in YAML format.
    * @return
    *   a list of `OpenAPICompatibilityIssue` instances detailing the compatibility issues found during verification, or `Nil` if no issues
    *   were found.
    */
  def verifyServer(serverEndpoints: List[AnyEndpoint], clientSpecificationYaml: String): List[OpenAPICompatibilityIssue] = {
    val serverOpenAPI = OpenAPIDocsInterpreter().toOpenAPI(serverEndpoints, "OpenAPIVerifier", "1.0")
    val clientOpenAPI = readOpenAPIFromString(clientSpecificationYaml)

    OpenAPIComparator(clientOpenAPI, serverOpenAPI).compare()
  }

  private def readOpenAPIFromString(yamlOpenApiSpec: String): OpenAPI = {
    parser.parse(yamlOpenApiSpec).flatMap(_.as[OpenAPI]) match {
      case Right(openapi) => openapi
      case Left(error)    => throw new IllegalArgumentException("Failed to parse OpenAPI YAML specification", error)
    }
  }
}
