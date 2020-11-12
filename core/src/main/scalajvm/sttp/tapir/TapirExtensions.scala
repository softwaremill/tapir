package sttp.tapir

import java.io.File
import java.nio.file.Path

trait TapirExtensions {
  type TapirFile = java.io.File
  def pathBody: EndpointIO.Body[File, Path] = binaryBody[File, Path]
}
