package sttp.tapir

import org.scalajs.dom.File

trait TapirExtensions {
  type TapirFile = org.scalajs.dom.File
}

object TapirFile {
  def name(f: TapirFile): String = f.name
}
