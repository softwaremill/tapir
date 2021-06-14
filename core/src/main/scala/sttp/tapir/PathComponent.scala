package sttp.tapir

import sttp.model.Method

sealed trait PathComponent

case object PathVariableSegment extends PathComponent

case object WildcardPathSegment extends PathComponent

case class FixedPathSegment(i: String) extends PathComponent

case class FixedMethod(m: Method) extends PathComponent

case object NotRelevantForShadowCheck extends PathComponent
