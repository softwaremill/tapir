package sttp.tapir.generic

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.AB2TA

import org.scalajs.dom.BlobPart
import org.scalajs.dom.File

trait MultipartCodecDerivationTestExtensions {

  // Mimic java.io.File operations used in tests so we can keep the tests simpler.
  implicit class FileOps(file: File) {
    def getName: String = file.name
    def delete(): Unit = {}
  }

  def createTempFile() = new File(
    Iterable(Array.empty[Byte].toTypedArray.asInstanceOf[BlobPart]).toJSIterable,
    "temp.txt"
  )
}
