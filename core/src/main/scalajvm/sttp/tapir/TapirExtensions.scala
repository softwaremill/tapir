package sttp.tapir

import java.nio.file.Path

trait TapirExtensions {
  type TapirFile = java.io.File
  def pathBody: EndpointIO.Body[FileRange, Path] = binaryBody(RawBodyType.FileBody)[Path]
}

object TapirFile {
  def name(f: TapirFile): String = f.getName
}
