package sttp.tapir

import java.nio.file.Path

trait TapirExtensions {
  type File = java.io.File
  def pathBody: EndpointIO.Body[FileRange, Path] = rawBinaryBody[FileRange].map(_.toFile.toPath)(d => FileRange.from(d.toFile))
  def fileBody: EndpointIO.Body[FileRange, File] = rawBinaryBody[FileRange].map(_.toFile)(d => FileRange.from(d))
}
