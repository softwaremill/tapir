package sttp.tapir

import sttp.tapir.EndpointInput.{PathCapture, Query}
import sttp.tapir.internal.UrlencodedData

object ShowPathTemplate {
  type ShowPathParam = (Int, PathCapture[_]) => String
  type ShowQueryParam = (Int, Query[_]) => String

  object Defaults {
    val path: ShowPathParam = (index, pc) => pc.name.map(name => s"{$name}").getOrElse(s"{param$index}")
    val query: ShowQueryParam = (_, q) => s"${q.name}={${q.name}}"
  }

  def apply(
      e: EndpointMetaOps
  )(showPathParam: ShowPathParam, showQueryParam: Option[ShowQueryParam], includeAuth: Boolean): String = {
    import sttp.tapir.internal._

    val inputs = e.securityInput.and(e.input).asVectorOfBasicInputs(includeAuth)
    val (pathComponents, pathParamCount) = shownPathComponents(inputs, showPathParam)
    val queryComponents = showQueryParam
      .map(shownQueryComponents(inputs, _, pathParamCount))
      .map(_.mkString("&"))
      .getOrElse("")

    "/" + pathComponents.mkString("/") + (if (queryComponents.isEmpty) "" else "?" + queryComponents)
  }

  private def shownPathComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      showPathParam: ShowPathParam
  ): (Vector[String], Int) =
    inputs.foldLeft((Vector.empty[String], 1)) { case ((acc, index), component) =>
      component match {
        case p: EndpointInput.PathCapture[_]  => (acc :+ showPathParam(index, p), index + 1)
        case EndpointInput.FixedPath(s, _, _) => (acc :+ UrlencodedData.encodePathSegment(s), index)
        case _                                => (acc, index)
      }
    }

  private def shownQueryComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      showQueryParam: ShowQueryParam,
      pathParamCount: Int
  ): Vector[String] =
    inputs
      .foldLeft((Vector.empty[String], pathParamCount)) { case ((acc, index), component) =>
        component match {
          case q: EndpointInput.Query[_] => (acc :+ showQueryParam(index, q), index + 1)
          case _                         => (acc, index)
        }
      }
      ._1
}
