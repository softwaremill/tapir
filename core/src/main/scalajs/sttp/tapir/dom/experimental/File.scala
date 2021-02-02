package sttp.tapir.dom.experimental

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

// Copied from sttp
// https://github.com/softwaremill/sttp/blob/620464db88b58d1c748bd589c3f9ef24918a71b7/core/src/main/scalajs/sttp/client3/dom/experimental/File.scala

// the File interface in scala.js does not match the spec
// https://developer.mozilla.org/en-US/docs/Web/API/File

@js.native
@JSGlobal
class File(
    parts: js.Array[js.Any] = js.native,
    override val name: String = js.native,
    options: FilePropertyBag = js.native
) extends org.scalajs.dom.File {
  val lastModified: Int = js.native
}

@js.native
@JSGlobal
object File extends js.Object

@js.native
trait FilePropertyBag extends js.Object {
  def `type`: String = js.native

  def lastModified: Int = js.native
}

object FilePropertyBag {
  @inline
  def apply(
      `type`: js.UndefOr[String] = js.undefined,
      lastModified: js.UndefOr[Int] = js.undefined
  ): FilePropertyBag = {
    val result = js.Dynamic.literal()
    `type`.foreach(result.`type` = _)
    lastModified.foreach(result.lastModified = _)
    result.asInstanceOf[FilePropertyBag]
  }
}
