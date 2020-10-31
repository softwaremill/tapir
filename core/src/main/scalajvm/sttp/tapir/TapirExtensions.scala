package sttp.tapir

import java.io.File
import java.nio.file.Path

trait TapirExtensions {
  def pathBody: EndpointIO.Body[File, Path] = binaryBody[File, Path]
}
