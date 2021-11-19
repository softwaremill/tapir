package sttp.tapir

import org.scalajs.dom.File

trait TapirExtensions {
  type TapirFile = org.scalajs.dom.File
}

object TapirExtensions {
  def fileName(f: TapirFile): String = f.name
}
