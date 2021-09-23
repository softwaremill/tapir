package sttp.tapir

import org.scalajs.dom.File

trait TapirExtensions {
  type File = org.scalajs.dom.File
  def fileBody: EndpointIO.Body[FileRange, File] = rawBinaryBody[FileRange].map(_.toFile)(d => FileRange.from(d))
}
