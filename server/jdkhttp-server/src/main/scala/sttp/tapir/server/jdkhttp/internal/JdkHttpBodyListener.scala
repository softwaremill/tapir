package sttp.tapir.server.jdkhttp
package internal

import sttp.tapir.Id
import sttp.tapir.server.interpreter.BodyListener

import java.io.InputStream
import scala.util.{Success, Try}

private[jdkhttp] class JdkHttpBodyListener extends BodyListener[Id, JdkHttpResponseBody] {
  override def onComplete(body: JdkHttpResponseBody)(cb: Try[Unit] => Unit): JdkHttpResponseBody = {
    val (is, maybeContentSize) = body

    (new CallbackAwareInputStream(is, cb), maybeContentSize)
  }
}

private class CallbackAwareInputStream(delegate: InputStream, cb: Try[Unit] => Unit) extends InputStream {

  private var alreadyTriggered = false

  override def read(): Int = {
    val r = delegate.read()
    if (r == -1 && !alreadyTriggered) {
      cb(Success(()))
      alreadyTriggered = true
    }

    r
  }
}
