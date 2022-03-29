# Using as an sttp client

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % "1.0.0-M5"
```

To make requests using an endpoint definition using the [sttp client](https://github.com/softwaremill/sttp), import:

```scala
import sttp.tapir.client.sttp.SttpClientInterpreter
```

This objects contains a number of variants for creating a client call, where the first parameter is the endpoint description.
The second is an optional URI - if this is `None`, the request will be relative.

Here's a summary of the available method variants; `R` are the requirements of the endpoint, such as streaming or websockets:

- `toRequest(PublicEndpoint, Option[Uri])`: returns a function, which represents decoding errors as the `DecodeResult`
  class.
  ```scala
  I => Request[DecodeResult[Either[E, O]], R]
  ```
  After providing the input parameters, a description of the request to be made is returned, with the input value
  encoded as appropriate request parameters: path, query, headers and body. This can be further
  customised and sent using any sttp backend. The response will then contain the decoded error or success values
  (note that this can be the body enriched with data from headers/status code).
- `toRequestThrowDecodeFailures(PublicEndpoint, Option[Uri])`: returns a function, which will throw an exception or 
  return a failed effect if decoding of the result fails
  ```scala
  I => Request[Either[E, O], R]
  ```
- `toRequestThrowErrors(PublicEndpoint, Option[Uri])`: returns a function, which will throw an exception or
  return a failed effect if decoding of the result fails, or if the result is an error (as described by the endpoint) 
  ```scala
  I => Request[O, R]
  ```

Next, there are `toClient(PublicEndpoint, Option[Uri], SttpBackend[F, R])` methods (in the above variants), which
send the request using the given backend. Hence in this case, the signature of the result is:

```scala
I => F[DecodeResult[Either[E, O]]]
```

Finally, for secure endpoints, there are `toSecureClient` and `toSecureRequest` families of methods. They return
functions which first accept the security inputs, and then the regular inputs. For example:

```scala
// toSecureRequest(Endpoint, Option[Uri]) returns: 
A => I => Request[DecodeResult[Either[E, O]], R]

// toSecureClient(Endpoint, Option[Uri], SttpBackend) returns:
A => I => F[DecodeResult[Either[E, O]]]
```

See  the [runnable example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/BooksExample.scala)
for example usage.

## Web sockets

To interpret a web socket endpoint, an additional streams-specific import is needed, so that the interpreter can
convert sttp's `WebSocket` instance into a pipe. This logic is looked up via the `WebSocketToPipe` implicit.

The required imports are as follows:

```scala
import sttp.tapir.client.sttp.ws.akkahttp._ // for akka-streams
import sttp.tapir.client.sttp.ws.fs2._      // for fs2
import sttp.tapir.client.sttp.ws.zio._      // for zio 2.x
import sttp.tapir.client.sttp.ws.zio1._     // for zio 1.x
```

No additional dependencies are needed (except for zio1, which needs the `tapir-sttp-client-ws-zio1` dependency), as 
both of the above implementations are included in the main interpreter, with dependencies on akka-streams, fs2 and zio 
being marked as optional (hence these are not transitive).

## Scala.JS

In this case add the following dependencies (note the [`%%%`](https://www.scala-js.org/doc/project/dependencies.html) 
instead of the usual `%%`):

```scala
"com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % "1.0.0-M5"
"io.github.cquiroz" %%% "scala-java-time" % "2.2.0" // implementations of java.time classes for Scala.JS
```

The client interpreter also supports Scala.JS, the request must then be sent using the
[sttp client Scala.JS Fetch backend](https://sttp.softwaremill.com/en/latest/backends/javascript/fetch.html).

You can check the [`SttpClientTests`](https://github.com/softwaremill/tapir/blob/master/client/sttp-client/src/test/scalajs/sttp/tapir/client/sttp/SttpClientTests.scala) for a working example.
