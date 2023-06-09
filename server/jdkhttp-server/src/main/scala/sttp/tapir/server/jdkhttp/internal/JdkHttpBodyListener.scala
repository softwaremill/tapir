package sttp.tapir.server.jdkhttp
package internal

import com.sun.net.httpserver.HttpExchange
import sttp.tapir.server.interpreter.BodyListener

import java.io.InputStream
import scala.util.{Success, Try}

private[jdkhttp] class JdkHttpBodyListener(exchange: HttpExchange) extends BodyListener[Id, JdkHttpResponseBody] {
  override def onComplete(body: JdkHttpResponseBody)(cb: Try[Unit] => Unit): JdkHttpResponseBody = {
    // There's no direct way to call back when the response has been sent
    // The caller should close the response OutputStream when done
    cb(Success(()))
    body
  }
}
