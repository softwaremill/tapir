package sttp.tapir

import sttp.model.headers.Range

case class FileRange(underlying: Any, range: Option[Range] = None) extends FileRangeExtension

object FileRange extends FileRangeCompanionExtensions
