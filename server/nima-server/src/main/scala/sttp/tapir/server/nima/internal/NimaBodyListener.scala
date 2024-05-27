package sttp.tapir.server.nima.internal

import io.helidon.webserver.http.{ServerResponse => JavaNimaServerResponse}
import sttp.tapir.Identity
import sttp.tapir.server.interpreter.BodyListener

import java.io.InputStream
import scala.util.{Success, Try}

private[nima] class NimaBodyListener(res: JavaNimaServerResponse) extends BodyListener[Identity, InputStream] {
  override def onComplete(body: InputStream)(cb: Try[Unit] => Unit): InputStream = {
    res.whenSent(() => cb(Success(())))
    body
  }
}
