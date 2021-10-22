package sttp.tapir

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.AB2TA

import sttp.tapir.dom.experimental.{File => DomFileWithBody}

object Defaults {
  def createTempFile: () => TapirFile = () =>
    new DomFileWithBody(
      Array(Array.empty[Byte].toTypedArray.asInstanceOf[js.Any]).toJSArray,
      "temp.txt"
    )
}
