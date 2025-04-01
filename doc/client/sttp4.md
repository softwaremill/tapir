# Using as an sttp4 client

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % "@VERSION@"
```

To make requests using an endpoint definition using the [sttp client](https://github.com/softwaremill/sttp),
firstly one needs to understand some changes introduced with sttp 4. 
Describing the requests using this library now results in receiving dedicated types for "basic" HTTP requests (`Request`), 
streaming requests (`StreamRequest`) and web socket requests (`WebSocketRequest`). Each of this requests types can be sent using
a matching backend (`Backend`/`StreamBackend`/`WebSocketBackend`).

In order to use this client import one of the appropriate interpreters for your use case:
```scala mdoc:compile-only
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.client.sttp4.ws.WebSocketSttpClientInterpreter
import sttp.tapir.client.sttp4.streaming.StreamingSttpClientInterpreter
```

These objects contain a number of variants for creating a client call, where the first parameter is the endpoint description.
The second is an optional URI - if this is `None`, the request will be relative.

Here's a summary of the available method variants; `R`(for WebSocket requests) or `S`(for Streaming requests)
are the requirements of the endpoint.  
In case of a "basic" `SttpClientInterpreter` which is to be used for "basic" requests, no additional requirements are necessary, hence
`R` is equal to `Any` and the methods signatures contain this value in several places.  
**NOTE:**  
`Request` will be used below to describe the available interpreter's methods for simplicity. `StreamRequest` equivalents will be put next to it for comparison.

- `toRequest(PublicEndpoint, Option[Uri])`: returns a function, which represents decoding errors as the `DecodeResult`
  class.
  ```scala
  I => Request[DecodeResult[Either[E, O]]] // I => StreamRequest[DecodeResult[Either[E, O]], S]
  ```
  After providing the input parameters, a description of the request to be made is returned, with the input value
  encoded as appropriate request parameters: path, query, headers and body. This can be further
  customised and sent using a dedicated sttp backend (as mentioned in the opening section). The response will then contain the decoded error or success values
  (note that this can be the body enriched with data from headers/status code).  


- `toRequestThrowDecodeFailures(PublicEndpoint, Option[Uri])`: returns a function, which will throw an exception or 
  return a failed effect if decoding of the result fails
  ```scala
  I => Request[Either[E, O]] // I => StreamRequest[Either[E, O], S]
  ```
- `toRequestThrowErrors(PublicEndpoint, Option[Uri])`: returns a function, which will throw an exception or
  return a failed effect if decoding of the result fails, or if the result is an error (as described by the endpoint) 
  ```scala
  I => Request[O] // I => StreamRequest[O, S]
  ```

Next, there are `toClient(PublicEndpoint, Option[Uri], Backend[F])` methods (in the above variants), which
send the request using the given backend. Hence in this case, the signature of the result is:

```scala
I => F[DecodeResult[Either[E, O]]]
```

Finally, for secure endpoints, there are `toSecureClient` and `toSecureRequest` families of methods. They return
functions which first accept the security inputs, and then the regular inputs. For example:

```scala
// toSecureRequest(Endpoint, Option[Uri]) returns: 
A => I => Request[DecodeResult[Either[E, O]]] // A => I => StreamRequest[DecodeResult[Either[E, O]], S]

// toSecureClient(Endpoint, Option[Uri], SttpBackend) returns:
A => I => F[DecodeResult[Either[E, O]]]
```

## Web sockets

To interpret a web socket endpoint, an additional streams-specific import is needed, so that the interpreter can
convert sttp's `WebSocket` instance into a pipe. This logic is looked up via the `WebSocketToPipe` implicit.

The required imports are as follows:

```scala
import sttp.tapir.client.sttp4.ws.pekkohttp.* // for pekko-streams
import sttp.tapir.client.sttp4.ws.fs2.*      // for fs2
import sttp.tapir.client.sttp4.ws.zio.*      // for zio
```

No additional dependencies are needed, as both of the above implementations are included in the main interpreter, 
with dependencies on pekko-streams, akka-streams, fs2 and zio being marked as optional (hence these are not transitive).

## Overwriting the response specification

The `Request` obtained from the `.toRequest` and `.toSecureRequest` families of methods, after being applied to the 
input, contains both the request data (URI, headers, body), and a description of how to handle the response -
depending on the variant used, decoding the response into one of endpoint's outputs.

If you'd like to skip that step, e.g. when testing redirects, it's possible to overwrite the response handling 
description, for example:

```scala :compile-only
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
"com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % "@VERSION@"
"io.github.cquiroz" %%% "scala-java-time" % "2.2.0" // implementations of java.time classes for Scala.JS
```

The client interpreter also supports Scala.JS, the request must then be sent using the
[sttp client Scala.JS Fetch backend](https://sttp.softwaremill.com/en/latest/backends/javascript/fetch.html).

## Limitations

There are limitations existing on some clients that prevent the description generated by tapir from being decoded correctly.
For security reasons the [`Set-Cookie`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie) header is not accessible from frontend JavaScript code.

It means that any endpoint containing a `.out(setCookie("token"))` will fail to be decoded on the client side when using Fetch.
A solution is to use the `setCookieOpt` function instead an let the browser do its job when dealing with cookies.
