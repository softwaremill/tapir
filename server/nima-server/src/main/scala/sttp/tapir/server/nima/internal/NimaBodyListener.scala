package sttp.tapir.server.nima.internal

import io.helidon.webserver.http.{ServerResponse => JavaNimaServerResponse}
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.nima.Id

import java.io.InputStream
import scala.util.{Success, Try}

private[nima] class NimaBodyListener(res: JavaNimaServerResponse) extends BodyListener[Id, InputStream] {
  override def onComplete(body: InputStream)(cb: Try[Unit] => Unit): InputStream = {
    res.whenSent(() => cb(Success(())))
    body
  }
}
