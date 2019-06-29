package tapir

import tapir.EndpointInput.{PathCapture, Query}

object RenderPathTemplate {
  type PathParamRendering = (Int, PathCapture[_]) => String
  type QueryParamRendering = (Int, Query[_]) => String

  object Defaults {
    val path: PathParamRendering = (index, pc) => pc.name.map(name => s"{$name}").getOrElse(s"{param$index}")
    val query: QueryParamRendering = (_, q) => s"${q.name}={${q.name}}"
  }

  def apply(e: Endpoint[_, _, _, _])(pathParamRendering: PathParamRendering, queryParamRendering: Option[QueryParamRendering]): String = {
    import tapir.internal._

    val inputs = e.input.asVectorOfBasicInputs(includeAuth = false)
    val (pathComponents, pathParamCount) = renderedPathComponents(inputs, pathParamRendering)
    val queryComponents = queryParamRendering
      .map(renderedQueryComponents(inputs, _, pathParamCount))
      .map(_.mkString("&"))
      .getOrElse("")

    "/" + pathComponents.mkString("/") + (if (queryComponents.isEmpty) "" else "?" + queryComponents)
  }

  private def renderedPathComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      pathParamRendering: PathParamRendering
  ): (Vector[String], Int) =
    inputs.foldLeft((Vector.empty[String], 1)) {
      case ((acc, index), component) =>
        component match {
          case p: EndpointInput.PathCapture[_] => (acc :+ pathParamRendering(index, p), index + 1)
          case EndpointInput.FixedPath(s)      => (acc :+ s, index)
          case _                               => (acc, index)
        }
    }

  private def renderedQueryComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      queryParamRendering: QueryParamRendering,
      pathParamCount: Int
  ): Vector[String] =
    inputs
      .foldLeft((Vector.empty[String], pathParamCount)) {
        case ((acc, index), component) =>
          component match {
            case q: EndpointInput.Query[_] => (acc :+ queryParamRendering(index, q), index + 1)
            case _                         => (acc, index)
          }
      }
      ._1
}
