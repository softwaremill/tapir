package sttp.tapir.docs.asyncapi

import sttp.apispec._
import sttp.apispec.asyncapi.MessageExample
import sttp.tapir.{Codec, EndpointIO}
import sttp.ws.WebSocketFrame

private[asyncapi] object ExampleConverter {
  def convertExamples[T](c: Codec[WebSocketFrame, T, _], examples: List[EndpointIO.Example[T]]): List[MessageExample] = {
    examples
      .flatMap { example =>
        c.encode(example.value) match {
          case WebSocketFrame.Text(payload, _, _) =>
            Some(MessageExample(headers = None, Some(ExampleSingleValue(payload)), example.name, example.summary))
          case WebSocketFrame.Binary(_, _, _) => None
          case _: WebSocketFrame.Control => None
        }
      }
  }
}
