package sttp.tapir.docs.asyncapi

import sttp.apispec._
import sttp.tapir.{Codec, EndpointIO}
import sttp.ws.WebSocketFrame

private[asyncapi] object ExampleConverter {
  def convertExamples[T](c: Codec[WebSocketFrame, T, _], examples: List[EndpointIO.Example[T]]): List[ExampleValue] = {
    examples
      .flatMap { example =>
        val exampleValue = c.encode(example.value) match {
          case WebSocketFrame.Text(payload, _, _) => Some(payload)
          case WebSocketFrame.Binary(_, _, _)     => None
          case _: WebSocketFrame.Control          => None
        }

        exampleValue.map(ExampleValue.string)
      }
  }
}
