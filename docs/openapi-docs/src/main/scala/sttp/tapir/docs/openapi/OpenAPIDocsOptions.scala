package sttp.tapir.docs.openapi

import sttp.model.{Method, StatusCode}
import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.docs.apispec.defaultSchemaName
import sttp.tapir.docs.openapi.EndpointInputToDecodeFailureOutput.defaultBadRequestDescription
import sttp.tapir.{EndpointInput, EndpointOutput, statusCode, stringBody}

case class OpenAPIDocsOptions(
    operationIdGenerator: (Vector[String], Method) => String,
    schemaName: SObjectInfo => String = defaultSchemaName,
    referenceEnums: SObjectInfo => Boolean = _ => false,
    defaultDecodeFailureOutput: EndpointInput[_] => Option[EndpointOutput[_]] = OpenAPIDocsOptions.defaultDecodeFailureOutput
)

object OpenAPIDocsOptions {
  val defaultOperationIdGenerator: (Vector[String], Method) => String = { (pathComponents, method) =>
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
