package sttp.tapir

import sttp.tapir.EndpointInput.{PathCapture, Query}
import sttp.tapir.internal.UrlencodedData

object RenderPathTemplate {
  type RenderPathParam = (Int, PathCapture[_]) => String
  type RenderQueryParam = (Int, Query[_]) => String

  object Defaults {
    val path: RenderPathParam = (index, pc) => pc.name.map(name => s"{$name}").getOrElse(s"{param$index}")
    val query: RenderQueryParam = (_, q) => s"${q.name}={${q.name}}"
  }

  def apply(
      e: EndpointMetaOps
  )(renderPathParam: RenderPathParam, renderQueryParam: Option[RenderQueryParam], includeAuth: Boolean): String = {
    import sttp.tapir.internal._

    val inputs = e.securityInput.and(e.input).asVectorOfBasicInputs(includeAuth)
    val (pathComponents, pathParamCount) = renderedPathComponents(inputs, renderPathParam)
    val queryComponents = renderQueryParam
      .map(renderedQueryComponents(inputs, _, pathParamCount))
      .map(_.mkString("&"))
      .getOrElse("")

    "/" + pathComponents.mkString("/") + (if (queryComponents.isEmpty) "" else "?" + queryComponents)
  }

  private def renderedPathComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      pathParamRendering: RenderPathParam
  ): (Vector[String], Int) =
    inputs.foldLeft((Vector.empty[String], 1)) { case ((acc, index), component) =>
      component match {
        case p: EndpointInput.PathCapture[_]  => (acc :+ pathParamRendering(index, p), index + 1)
        case EndpointInput.FixedPath(s, _, _) => (acc :+ UrlencodedData.encodePathSegment(s), index)
        case _                                => (acc, index)
      }
    }

  private def renderedQueryComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      queryParamRendering: RenderQueryParam,
      pathParamCount: Int
  ): Vector[String] =
    inputs
      .foldLeft((Vector.empty[String], pathParamCount)) { case ((acc, index), component) =>
        component match {
          case q: EndpointInput.Query[_] => (acc :+ queryParamRendering(index, q), index + 1)
          case _                         => (acc, index)
        }
      }
      ._1
}
