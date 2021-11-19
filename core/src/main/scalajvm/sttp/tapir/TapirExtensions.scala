package sttp.tapir

import java.nio.file.Path

trait TapirExtensions {
  type TapirFile = java.io.File
  def pathBody: EndpointIO.Body[FileRange, Path] = binaryBody[FileRange, Path]
}

object TapirExtensions {
  def fileName(f: TapirFile): String = f.getName
}
