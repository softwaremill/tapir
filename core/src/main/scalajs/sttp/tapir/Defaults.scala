package sttp.tapir

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.AB2TA

import org.scalajs.dom.BlobPart
import org.scalajs.dom.File

object Defaults {
  def createTempFile: () => TapirFile = () =>
    new File(
      Iterable(Array.empty[Byte].toTypedArray.asInstanceOf[BlobPart]).toJSIterable,
      "temp.txt"
    )
}
