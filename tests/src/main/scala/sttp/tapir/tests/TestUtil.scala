package sttp.tapir.tests

import java.io.InputStream

trait TestUtil extends TestUtilExtensions {
  def inputStreamToByteArray(is: InputStream): Array[Byte] = Iterator.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray
}

object TestUtil extends TestUtil
