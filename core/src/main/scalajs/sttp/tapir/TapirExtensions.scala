package sttp.tapir

import sttp.tapir.internal.TapirFile

import org.scalajs.dom.File

trait TapirExtensions {
  def fileBody: EndpointIO.Body[TapirFile, File] = rawBinaryBody[TapirFile].map(_.toFile)(d => TapirFile.fromFile(d))
}
