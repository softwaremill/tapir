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

```scala
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
decoded, but this can be customized through methods on `webSocketBody`.

## Close frames

If you are using the default codecs between `WebSocketFrame` and your high-level types, and you'd like to either be
notified that a websocket has been closed by the client, or close it from the server side, then you should wrap your
high-level type into an `Option`.

The default codecs map close frames to `None`, and regular (decoded text/binary) frames to `Some`. Hence, using the 
following definition:

```scala
webSocketBody[Option[String], CodecFormat.TextPlain, Option[Response], CodecFormat.Json](PekkoStreams)
```

the websocket-processing pipe will receive a `None: Option[String]` when the client closes the web socket. Moreover, 
if the pipe emits a `None: Option[Response]`, the web socket will be closed by the server.

Alternatively, if the codec for your high-level type already handles close frames (but its schema is not derived as
optional), you can request that the close frames are decoded by the codec as well. Here's an example which does this
on the server side:

```scala
webSocketBody[...](...).decodeCloseRequests(true)
```

If you'd like to decode close frames when the endpoint is interpreted as a client, you should use the 
`decodeCloseResponses` method.

```{note}
Not all server interpreters expose control frames (such as close frames) to user (and Tapir) code. Refer to the 
documentation of individual interpreters for more details.
```

## Raw web sockets

The second web socket handling variant is to obtain a raw pipe transforming `WebSocketFrame`s: 

```scala
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
passes close frames to the pipe as well. As before, this can be customized by methods on the returned output.

Request/response schemas can be customized through `.requestsSchema` and `.responsesSchema`.

## Interpreting as a server

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
