package sttp.tapir

case class FileRange(underlying: Any, range: Option[RangeValue] = None) extends FileRangeExtension

object FileRange extends FileRangeCompanionExtensions
