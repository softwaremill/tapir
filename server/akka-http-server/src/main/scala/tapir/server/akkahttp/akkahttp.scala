package tapir.server

import akka.stream.scaladsl.Source
import akka.util.ByteString

package object akkahttp extends TapirAkkaHttpServer {
  type AkkaStream = Source[ByteString, Any]
}
