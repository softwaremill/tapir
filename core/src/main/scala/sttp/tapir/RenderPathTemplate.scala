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
  )(
      renderPathParam: RenderPathParam,
      renderQueryParam: Option[RenderQueryParam],
      includeAuth: Boolean,
      showNoPathAs: String,
      showPathsAs: Option[String],
      showQueryParamsAs: Option[String]
  ): String = {
    import sttp.tapir.internal._

    val inputs = e.securityInput.and(e.input).asVectorOfBasicInputs(includeAuth)
    val (pathComponents, pathParamCount) = renderedPathComponents(inputs, renderPathParam, showPathsAs)
    val queryComponents = renderQueryParam
      .map(renderedQueryComponents(inputs, _, pathParamCount, showQueryParamsAs))
      .map(_.mkString("&"))
      .getOrElse("")

    val path = if (pathComponents.isEmpty) showNoPathAs else "/" + pathComponents.mkString("/")

    path + (if (queryComponents.isEmpty) "" else "?" + queryComponents)
  }

  private def renderedPathComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      pathParamRendering: RenderPathParam,
      showPathsAs: Option[String]
  ): (Vector[String], Int) =
    inputs.foldLeft((Vector.empty[String], 1)) { case ((acc, index), component) =>
      component match {
        case p: EndpointInput.PathCapture[_]  => (acc :+ pathParamRendering(index, p), index + 1)
        case _: EndpointInput.PathsCapture[_] => (showPathsAs.fold(acc)(acc :+ _), index)
        case EndpointInput.FixedPath(s, _, _) => (acc :+ UrlencodedData.encodePathSegment(s), index)
        case _                                => (acc, index)
      }
    }

  private def renderedQueryComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      queryParamRendering: RenderQueryParam,
      pathParamCount: Int,
      showQueryParamsAs: Option[String]
  ): Vector[String] =
    inputs
      .foldLeft((Vector.empty[String], pathParamCount)) { case ((acc, index), component) =>
        component match {
          case q: EndpointInput.Query[_]       => (acc :+ queryParamRendering(index, q), index + 1)
          case _: EndpointInput.QueryParams[_] => (showQueryParamsAs.fold(acc)(acc :+ _), index)
          case _                               => (acc, index)
        }
      }
      ._1
}
