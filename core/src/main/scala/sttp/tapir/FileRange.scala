package sttp.tapir

import sttp.model.headers.Range

case class FileRange(file: TapirFile, range: Option[Range] = None)
