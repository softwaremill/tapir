package sttp.tapir.tests

sealed trait Color
case object Blue extends Color
case object Red extends Color

case class ColorValue(color: Color, value: Int)

case class ColorWrapper(color: Color)
