package sttp.tapir.docs.openapi

import sttp.model.Method

/**
  * @param linkFromChildToParent Should child schemas contain a backlink to the parent schema using `allOf`, in addition
  *                              to the parent enumerating all children using `oneOf`.
  */
case class OpenAPIDocsOptions(operationIdGenerator: (Vector[String], Method) => String, linkFromChildToParent: Boolean)

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

  implicit val default: OpenAPIDocsOptions = OpenAPIDocsOptions(defaultOperationIdGenerator, linkFromChildToParent = true)
}
