package tapir.docs.openapi
import tapir.Method

case class OpenAPIDocsOptions(operationIdGenerator: (Vector[String], Method) => String)

object OpenAPIDocsOptions {
  val defaultOperationIdGenerator: (Vector[String], Method) => String = { (pathComponents, method) =>
    val pathComponentsOrRoot = if (pathComponents.isEmpty) {
      Vector("root")
    } else {
      pathComponents
    }
    s"${pathComponentsOrRoot.mkString("-")}-${method.m.toLowerCase}"
  }

  implicit val default: OpenAPIDocsOptions = OpenAPIDocsOptions(defaultOperationIdGenerator)
}
