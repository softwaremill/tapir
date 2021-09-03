package sttp.tapir

import sttp.tapir.internal.TapirFile

import java.io.File
import java.nio.file.Path

trait TapirExtensions {
  def pathBody: EndpointIO.Body[TapirFile, Path] = rawBinaryBody[TapirFile].map(_.toPath)(d => TapirFile.fromPath(d))
  def fileBody: EndpointIO.Body[TapirFile, File] = rawBinaryBody[TapirFile].map(_.toFile)(d => TapirFile.fromFile(d))
}
