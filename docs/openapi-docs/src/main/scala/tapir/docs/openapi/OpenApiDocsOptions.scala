package tapir.docs.openapi
import tapir.Method

case class OpenApiDocsOptions(operationIdGenerator: (Vector[String], Method) => String)

object OpenApiDocsOptions {
  val DefaultOperationIdGenerator: (Vector[String], Method) => String = { (pathComponents, method) =>
    val pathComponentsOrRoot = if (pathComponents.isEmpty) {
      Vector("root")
    } else {
      pathComponents
    }
    s"${pathComponentsOrRoot.mkString("-")}-${method.m.toLowerCase}"
  }

  implicit val Default = OpenApiDocsOptions(DefaultOperationIdGenerator)
}
