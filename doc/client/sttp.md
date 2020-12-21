# Using as an sttp client

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % "@VERSION@"
```

To make requests using an endpoint definition using the [sttp client](https://github.com/softwaremill/sttp), import:

```scala mdoc:compile-only
import sttp.tapir.client.sttp.SttpClientInterpreter
```

This objects contains two methods:
 - `toRequestUnsafe(Endpoint, Uri)`: given the base URI returns a function, which might throw an exception if 
   decoding of the result fails
   ```scala
   I => Request[Either[E, O], Any]
   ```
 - `toRequest(Endpoint, Uri)`: given the base URI returns a function, which represents decoding errors as the `DecodeResult` 
   class
   ```scala
   I => Request[DecodeResult[Either[E, O]], Any]
   ```

Note that the returned functions have one-argument: the input values of end endpoint. This might be a
single type, a tuple, or a case class, depending on the endpoint description.

After providing the input parameters, a description of the request to be made is returned, with the input value
encoded as appropriate request parameters: path, query, headers and body. This can be further 
customised and sent using any sttp backend. The response will then contain the decoded error or success values
(note that this can be the body enriched with data from headers/status code).

See  the [runnable example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/BooksExample.scala)
for example usage.

## Web sockets

To interpret a web socket enddpoint, an additional streams-specific import is needed, so that the interpreter can
convert sttp's `WebSocket` instance into a pipe. This logic is looked up via the `WebSocketToPipe` implicit.

The required imports are as follows:

```scala mdoc:compile-only
import sttp.tapir.client.sttp.ws.akkahttp._ // for akka-streams
import sttp.tapir.client.sttp.ws.fs2._      // for fs2
```

No additional dependencies are needed, as both of the above implementations are included in the main interpreter,]
with dependencies on akka-streams and fs2 being marked as optional (hence these are not transitive).

## Scala.JS

The client interpreter also supports Scala.JS, the request must then be send using the 
[sttp client Scala.JS Fetch backend](https://sttp.softwaremill.com/en/latest/backends/javascript/fetch.html).