package sttp.tapir.server

import _root_.play.api.http.HttpEntity
import _root_.play.api.http.websocket.Message
import akka.stream.scaladsl.Flow

package object play {
  type PlayResponseBody = Either[Flow[Message, Message, Any], HttpEntity]
}
