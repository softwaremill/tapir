package tapir.server

import akka.stream.scaladsl.Source
import akka.util.ByteString

package object akkahttp extends AkkaHttpServer {
  type AkkaStream = Source[ByteString, Any]
}
