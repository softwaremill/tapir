package sttp.tapir.tests

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.typedarray.AB2TA
import org.scalajs.dom.File
import sttp.tapir.dom.experimental.{File => DomFileWithBody}

@js.native
trait Blob extends js.Object {
  def text(): scala.scalajs.js.Promise[String] = js.native
}

trait TestUtilExtensions {
  def writeToFile(s: String): File = {
    new DomFileWithBody(
      Array(s.getBytes.toTypedArray.asInstanceOf[js.Any]).toJSArray,
      "test.tapir"
    )
  }

  def readFromFile(f: File): Future[String] = {
    f.asInstanceOf[Blob].text().toFuture
  }
}
