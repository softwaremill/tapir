package sttp.tapir.docs.openapi

import sttp.model.Method
import sttp.tapir.SchemaType.SObjectInfo
import sttp.tapir.docs.apispec.defaultSchemaName

case class OpenAPIDocsOptions(
    operationIdGenerator: (Vector[String], Method) => String,
    schemaName: SObjectInfo => String = defaultSchemaName,
    referenceEnums: SObjectInfo => Boolean = _ => false
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

  implicit val default: OpenAPIDocsOptions = OpenAPIDocsOptions(defaultOperationIdGenerator)
}
