package sttp.tapir

import sttp.model.headers.Range

case class FileRange(file: File, range: Option[Range] = None)
