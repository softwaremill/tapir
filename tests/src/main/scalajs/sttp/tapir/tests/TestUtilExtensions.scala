package sttp.tapir.tests

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.AB2TA

import org.scalajs.dom.BlobPart
import org.scalajs.dom.File

@js.native
trait Blob extends js.Object {
  def text(): scala.scalajs.js.Promise[String] = js.native
}

trait TestUtilExtensions {
  def writeToFile(s: String): File = {
    new File(
      Iterable(s.getBytes.toTypedArray.asInstanceOf[BlobPart]).toJSIterable,
      "test.tapir"
    )
  }

  def readFromFile(f: File): Future[String] = {
    f.asInstanceOf[Blob].text().toFuture
  }
}
