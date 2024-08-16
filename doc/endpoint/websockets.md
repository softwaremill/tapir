# Web sockets

Web sockets are supported through stream pipes, converting a stream of incoming messages to a stream of outgoing
messages. That's why web socket endpoints require both the `Streams` and `WebSocket` capabilities (see
[streaming support](streaming.md) for more information on streams).

## Typed web sockets

Web sockets outputs can be used in two variants. In the first, both requests and responses are handled by 
[codecs](codecs.md). Typically, a codec handles either text or binary messages, signalling decode failure (which
closes the web socket), if an unsupported frame is passed to decoding.

For example, here's an endpoint where the requests are strings (hence only text frames are handled), and the responses
are parsed/formatted as json:

```scala mdoc:silent
import sttp.tapir.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*

case class Response(msg: String, count: Int)
endpoint.out(
  webSocketBody[String, CodecFormat.TextPlain, Response, CodecFormat.Json](PekkoStreams))
```

When creating a `webSocketBody`, we need to provide the following parameters:
* the type or requests, along with its codec format (which is used to lookup the appropriate codec, as well as 
  determines the media type in documentation)
* the type of responses, along with its codec format
* the `Streams` implementation, which determines the pipe type

By default, ping-pong frames are handled automatically, fragmented frames are combined, and close frames aren't
decoded, but this can be customised through methods on `webSocketBody`.

## Raw web sockets

Alternatively, it's possible to obtain a raw pipe transforming `WebSocketFrame`s: 

```scala mdoc:silent
import org.apache.pekko.stream.scaladsl.Flow
import sttp.tapir.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.capabilities.WebSockets
import sttp.ws.WebSocketFrame

endpoint.out(webSocketBodyRaw(PekkoStreams)): PublicEndpoint[
  Unit, 
  Unit, 
  Flow[WebSocketFrame, WebSocketFrame, Any], 
  PekkoStreams with WebSockets]
```

Such a pipe by default doesn't handle ping-pong frames automatically, doesn't concatenate fragmented flames, and
passes close frames to the pipe as well. As before, this can be customised by methods on the returned output.

Request/response schemas can be customised through `.requestsSchema` and `.responsesSchema`.

## Interpreting as a sever

When interpreting a web socket endpoint as a server, the [server logic](../server/logic.md) needs to provide a
streaming-specific pipe from requests to responses. E.g. in Pekko's case, this will be `Flow[REQ, RESP, Any]`.

Refer to the documentation of interpreters for more details, as not all interpreters support all settings.

## Interpreting as a client

When interpreting a web socket endpoint as a client, after applying the input parameters, the result is a pipe
representing message processing as it happens on the server.

Refer to the documentation of interpreters for more details, as there are interpreter-specific additional requirements.

## Interpreting as documentation

Web socket endpoints can be interpreted into [AsyncAPI documentation](../docs/asyncapi.md).

## Determining if the request is a web socket upgrade

The `isWebSocket` endpoint input can be used to determine if the request contains the web socket upgrade headers.
The input only impacts server interpreters, doesn't affect documentation and its value is discarded by client
interpreters. 

## Next

Read on about [datatypes integrations](integrations.md).
