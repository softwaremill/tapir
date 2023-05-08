package sttp.tapir.docs.asyncapi

import sttp.tapir.AnyEndpoint
import sttp.tapir.Schema.SName
import sttp.tapir.docs.apispec.schema.defaultSchemaName

case class AsyncAPIDocsOptions(
    subscribeOperationId: (Vector[String], AnyEndpoint) => String,
    publishOperationId: (Vector[String], AnyEndpoint) => String,
    schemaName: SName => String = defaultSchemaName
)

object AsyncAPIDocsOptions {
  val defaultOperationIdGenerator: String => (Vector[String], AnyEndpoint) => String = { prefix => (pathComponents, _) =>
    val components = if (pathComponents.isEmpty) {
      Vector("root")
    } else {
      pathComponents
    }

    // converting to camelCase
    (prefix +: components.map(_.toLowerCase.capitalize)).mkString
  }

  val default: AsyncAPIDocsOptions = AsyncAPIDocsOptions(defaultOperationIdGenerator("on"), defaultOperationIdGenerator("send"))
}
