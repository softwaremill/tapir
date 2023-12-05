package sttp.tapir.internal

import sttp.tapir.EndpointInput.{PathCapture, Query}
import sttp.tapir.{EndpointInput, EndpointMetaOps}

private[tapir] object ShowPathTemplate {
  type ShowPathParam = (Int, PathCapture[_]) => String
  type ShowQueryParam = (Int, Query[_]) => String

  def apply(
      e: EndpointMetaOps
  )(
      showPathParam: ShowPathParam,
      showQueryParam: Option[ShowQueryParam],
      includeAuth: Boolean,
      showNoPathAs: String,
      showPathsAs: Option[String],
      showQueryParamsAs: Option[String],
      extraPara: Option[Int] = Some(3)
  ): String = {

    val inputs = e.securityInput.and(e.input).asVectorOfBasicInputs(includeAuth)
    val (pathComponents, pathParamCount) = shownPathComponents(inputs, showPathParam, showPathsAs)
    val queryComponents = showQueryParam
      .map(shownQueryComponents(inputs, _, pathParamCount, showQueryParamsAs))
      .map(_.mkString("&"))
      .getOrElse("")

    val path = if (pathComponents.isEmpty) showNoPathAs else "/" + pathComponents.mkString("/")

    path + (if (queryComponents.isEmpty) "" else "?" + queryComponents)
  }

  private def shownPathComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      showPathParam: ShowPathParam,
      showPathsAs: Option[String]
  ): (Vector[String], Int) =
    inputs.foldLeft((Vector.empty[String], 1)) { case ((acc, index), component) =>
      component match {
        case p: EndpointInput.PathCapture[_] if !p.codec.schema.hidden  => (acc :+ showPathParam(index, p), index + 1)
        case p: EndpointInput.PathsCapture[_] if !p.codec.schema.hidden => (showPathsAs.fold(acc)(acc :+ _), index)
        case p: EndpointInput.FixedPath[_] if !p.codec.schema.hidden    => (acc :+ UrlencodedData.encodePathSegment(p.s), index)
        case _                                                          => (acc, index)
      }
    }

  private def shownQueryComponents(
      inputs: Vector[EndpointInput.Basic[_]],
      showQueryParam: ShowQueryParam,
      pathParamCount: Int,
      showQueryParamsAs: Option[String]
  ): Vector[String] =
    inputs
      .foldLeft((Vector.empty[String], pathParamCount)) { case ((acc, index), component) =>
        component match {
          case q: EndpointInput.Query[_] if !q.codec.schema.hidden       => (acc :+ showQueryParam(index, q), index + 1)
          case q: EndpointInput.QueryParams[_] if !q.codec.schema.hidden => (showQueryParamsAs.fold(acc)(acc :+ _), index)
          case _                                                         => (acc, index)
        }
      }
      ._1
}
