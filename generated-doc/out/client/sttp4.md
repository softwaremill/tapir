# Using as an sttp client (v4)

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-client4" % "1.11.37"
```

To make requests using an endpoint definition using the [sttp client](https://github.com/softwaremill/sttp), import:

```scala
import sttp.tapir.client.sttp4.SttpClientInterpreter
```

This object contains a number of variants for creating a client call, where the first parameter is the endpoint description.
The second is an optional URI - if this is `None`, the request will be relative.

```{note}
If you've been using sttp client3, there have been some changes in the API. Notably, describing an HTTP request yields
different request types, depending on the backend capabilities that are required to send the request. Hence, there's a
`Request`, `StreamRequest` and `WebSocketRequest` type instead of a single `RequestT`. For a more detailed description 
of the changes, refer to sttp client's [migration docs](https://sttp.softwaremill.com/en/latest/migrate_v3_v4.html).
```

Here's a summary of the available method variants. Note that the below will work only for endpoints which do not 
require any additional capabilities (such as streaming or websockets), hence where the `R` type parameter is `Any`.
For other endpoints, see the sections on streaming and websockets below.

- `toRequest(PublicEndpoint, Option[Uri])`: returns a function, which represents decoding errors as the `DecodeResult`
  class.
  ```scala
  I => Request[DecodeResult[Either[E, O]]] 
  ```
  After providing the input parameters, a description of the request to be made is returned, with the input value
  encoded as appropriate request parameters: path, query, headers and body. This can be further customized and sent 
  using an sttp backend. The response will contain the decoded error or success values (note that this can be the 
  body enriched with data from headers/status code).  
- `toRequestThrowDecodeFailures(PublicEndpoint, Option[Uri])`: returns a function, which will throw an exception or 
  return a failed effect if decoding of the result fails
  ```scala
  I => Request[Either[E, O]] 
  ```
- `toRequestThrowErrors(PublicEndpoint, Option[Uri])`: returns a function, which will throw an exception or
  return a failed effect if decoding of the result fails, or if the result is an error (as described by the endpoint) 
  ```scala
  I => Request[O] 
  ```

Next, there are `toClient(PublicEndpoint, Option[Uri], Backend[F])` methods (with analogous variants), which
send the request using the given backend. Hence in this case, the signature of the result is:

```scala
I => F[DecodeResult[Either[E, O]]]
```

Finally, for secure endpoints, there are `toSecureClient` and `toSecureRequest` families of methods. They return
functions which first accept the security inputs, and then the regular inputs. For example:

```scala
// toSecureRequest(Endpoint, Option[Uri]) returns: 
A => I => Request[DecodeResult[Either[E, O]]] 

// toSecureClient(Endpoint, Option[Uri], SttpBackend) returns:
A => I => F[DecodeResult[Either[E, O]]]
```

## Streaming

To interpret a streaming endpoint, you'll need to use a different import:

```scala
import sttp.tapir.client.sttp4.stream.StreamSttpClientInterpreter
```

The `StreamSttpClientInterpreter` contains method analogous to the ones in the "basic" `SttpClientInterpreter`.
The difference is that the streaming interpreter works only for endpoints, which require the streaming capability:
that is, their `R` type parameter must be a subtype of `sttp.capabilities.Streams[_]`. Moreover, the result type
the request-creating methods is a `StreamRequest`, instead of a `Request`.

## Web sockets

To interpret a web socket endpoint, an additional streams-specific import is needed, so that the interpreter can
convert sttp's `WebSocket` instance into a pipe. This logic is looked up via the `WebSocketToPipe` implicit.

The required imports are as follows:

```scala
import sttp.tapir.client.sttp4.ws.WebSocketSttpClientInterpreter // mandatory

import sttp.tapir.client.sttp4.ws.pekkohttp.* // for pekko-streams
import sttp.tapir.client.sttp4.ws.fs2.*      // for fs2
import sttp.tapir.client.sttp4.ws.zio.*      // for zio
```

No additional dependencies are needed, as both of the above implementations are included in the main interpreter, 
with dependencies on pekko-streams, fs2 and zio being marked as optional (hence these are not transitive).

Just as with streaming, the request types returned by the `WebSocketSttpClientInterpreter` are of type 
`WebSocketRequest`, instead of `Request`.

## Overwriting the response specification

The `Request` obtained from the `.toRequest` and `.toSecureRequest` families of methods, after being applied to the 
input, contains both the request data (URI, headers, body), and a description of how to handle the response -
depending on the variant used, decoding the response into one of endpoint's outputs.

If you'd like to skip that step, e.g. when testing redirects, it's possible to overwrite the response handling 
description, for example:

```scala
import sttp.tapir.*
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.client4.*

SttpClientInterpreter()
  .toRequest(endpoint.get.in("hello").in(query[String]("name")), Some(uri"http://localhost:8080"))
  .apply("Ann")
  .response(asStringAlways)
```

## Scala.JS

In this case add the following dependencies (note the [`%%%`](https://www.scala-js.org/doc/project/dependencies.html) 
instead of the usual `%%`):

```scala
"com.softwaremill.sttp.tapir" %%% "tapir-sttp-client4" % "1.11.37"
"io.github.cquiroz" %%% "scala-java-time" % "2.2.0" // implementations of java.time classes for Scala.JS
```

The client interpreter also supports Scala.JS, the request must then be sent using the
[sttp client Scala.JS Fetch backend](https://sttp.softwaremill.com/en/latest/backends/javascript/fetch.html).

## Limitations

There are limitations existing on some clients that prevent the description generated by Tapir from being decoded 
correctly. For security reasons the [`Set-Cookie`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie) 
header is not accessible from frontend JavaScript code.

It means that any endpoint containing a `.out(setCookie("token"))` will fail to be decoded on the client side when 
using Fetch. A solution is to use the `setCookieOpt` function instead an let the browser do its job when dealing with 
cookies.
