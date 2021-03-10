package sttp.tapir.server

import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow

package object akkahttp {
  private[akkahttp] type AkkaResponseBody = Either[Flow[Message, Message, Any], ResponseEntity]
}
