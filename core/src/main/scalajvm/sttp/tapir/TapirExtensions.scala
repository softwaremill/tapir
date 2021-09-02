package sttp.tapir

import java.nio.file.Path

trait TapirExtensions {
  def pathBody: EndpointIO.Body[TapirFile, Path] = binaryBody[TapirFile, Path]
}
