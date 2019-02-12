package tapir.docs.openapi
import tapir.Method

case class OpenAPIDocsOptions(operationIdGenerator: (Vector[String], Method) => String)

object OpenAPIDocsOptions {
  val defaultOperationIdGenerator: (Vector[String], Method) => String = { (pathComponents, method) =>
    val components = if (pathComponents.isEmpty) {
      Vector("root")
    } else {
      pathComponents
    }

    // converting to camelCase
    (method.m.toLowerCase +: components.map(_.toLowerCase.capitalize)).mkString
  }

  implicit val default: OpenAPIDocsOptions = OpenAPIDocsOptions(defaultOperationIdGenerator)
}
