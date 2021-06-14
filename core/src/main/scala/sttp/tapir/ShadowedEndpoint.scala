package sttp.tapir

import sttp.model.Method

case class ShadowedEndpoint(e: Endpoint[_, _, _, _], by: Endpoint[_, _, _, _]) {
  override def toString = e.input.show + ", is shadowed by: " + by.input.show
}

private sealed trait PathComponent

private case object PathVariableSegment extends PathComponent

private case object WildcardPathSegment extends PathComponent

private case class FixedPathSegment(s: String) extends PathComponent

private case class FixedMethodComponent(m: Method) extends PathComponent
