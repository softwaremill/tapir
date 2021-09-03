package sttp.tapir.internal

import sttp.tapir.{RangeValue, TapirFileCompanionExtensions, TapirFileExtension}

case class TapirFile(underlying: Any, range: Option[RangeValue] = None) extends TapirFileExtension

object TapirFile extends TapirFileCompanionExtensions
