package sttp.tapir.docs.openapi

import sttp.model.{Method, StatusCode}
import sttp.tapir.Schema.SName
import sttp.tapir.docs.apispec.schema.defaultSchemaName
import sttp.tapir.docs.openapi.EndpointInputToDecodeFailureOutput.defaultBadRequestDescription
import sttp.tapir.{AnyEndpoint, EndpointInput, EndpointOutput, statusCode, stringBody}

case class OpenAPIDocsOptions(
    operationIdGenerator: (AnyEndpoint, Vector[String], Method) => String,
    schemaName: SName => String = defaultSchemaName,
    defaultDecodeFailureOutput: EndpointInput[_] => Option[EndpointOutput[_]] = OpenAPIDocsOptions.defaultDecodeFailureOutput,
    markOptionsAsNullable: Boolean = false,
    failOnDuplicateOperationId: Boolean = false,
    failOnDuplicateSchemaName: Boolean = false,
    // Stricter than failOnDuplicateSchemaName on purpose: marking a component is explicit, and its key defaults to the parameter's or
    // header's own name, so a collision means two structurally different things claiming one name - almost always a mistake, and one
    // only the author can resolve. Nothing can break, either: no existing code marks a component.
    failOnDuplicateComponentName: Boolean = true
)

object OpenAPIDocsOptions {
  val defaultOperationIdGenerator: (AnyEndpoint, Vector[String], Method) => String = { (_, pathComponents, method) =>
    val components = if (pathComponents.isEmpty) {
      Vector("root")
    } else {
      pathComponents
    }

    // converting to camelCase
    (method.method.toLowerCase +: components.map(_.toLowerCase.capitalize)).mkString
  }

  val defaultDecodeFailureOutput: EndpointInput[_] => Option[EndpointOutput[_]] = input =>
    defaultBadRequestDescription(input).map { description =>
      statusCode(StatusCode.BadRequest).and(stringBody.description(description))
    }

  val default: OpenAPIDocsOptions = OpenAPIDocsOptions(defaultOperationIdGenerator)
}
