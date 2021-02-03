package sttp.tapir.docs.openapi

import sttp.model.Method
import sttp.tapir.SchemaType.SObjectInfo

case class OpenAPIDocsOptions(
                               operationIdGenerator: (Vector[String], Method) => String,
                               schemaObjectInfoToNameMapper: SObjectInfo => String = SObjectInfo.defaultToNameMapper
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
