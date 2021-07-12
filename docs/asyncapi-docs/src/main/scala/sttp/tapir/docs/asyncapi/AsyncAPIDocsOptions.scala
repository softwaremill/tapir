package sttp.tapir.docs.asyncapi

import sttp.tapir.Endpoint
import sttp.tapir.Schema.SName
import sttp.tapir.docs.apispec.defaultSchemaName

case class AsyncAPIDocsOptions(
    subscribeOperationId: (Vector[String], Endpoint[_, _, _, _]) => String,
    publishOperationId: (Vector[String], Endpoint[_, _, _, _]) => String,
    schemaName: SName => String = defaultSchemaName
)

object AsyncAPIDocsOptions {
  val defaultOperationIdGenerator: String => (Vector[String], Endpoint[_, _, _, _]) => String = { prefix => (pathComponents, _) =>
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
