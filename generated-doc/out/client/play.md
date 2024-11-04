# Using as a Play client

Tapir supports both Play 2.9, which still ships with Akka, and Play 3.0, which replaces Akka with Pekko.
See the [Play framework documentation](https://www.playframework.com/documentation/2.9.x/General#How-Play-Deals-with-Akkas-License-Change) for differences between these versions.

For **Play 3.0**, add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-play-client" % "1.11.8"
```

For **Play 2.9**, add

```scala
"com.softwaremill.sttp.tapir" %% "tapir-play29-client" % "1.11.8"
```

instead. Furthermore, replace all uses of `sttp.capabilities.pekko.PekkoStreams` in the following code snippets with `sttp.capabilities.akka.AkkaStreams`.

To make requests using an endpoint definition using the [play client](https://github.com/playframework/play-ws), import:

```scala
import sttp.tapir.client.play.PlayClientInterpreter
```

This objects contains four methods:
 - `toRequestThrowDecodeFailures(PublicEndpoint, String)` and `toSecureRequestThrowDecodeErrors(Endpoint, String)`: given
   the base URI returns a function, which will generate a request and a response parser which might throw
   an exception when decoding of the result fails
   ```scala
   I => (StandaloneWSRequest, StandaloneWSResponse => Either[E, O])
   ```
 - `toRequest(PublicEndpoint, String)` and `toSecureRequest(Endpoint, String)`: given the base URI returns a function,
   which will generate a request and a response parser which represents
   decoding errors as the `DecodeResult` class
   ```scala
   I => (StandaloneWSRequest, StandaloneWSResponse => DecodeResult[Either[E, O]])
   ```

Note that the returned functions have one argument each: first the security inputs (if any), and regular input values of the endpoint. This might be a
single type, a tuple, or a case class, depending on the endpoint description.

After providing the input parameters, the two following are returned:
- a description of the request to be made, with the input value
  encoded as appropriate request parameters: path, query, headers and body.
  This can be further customised and sent using regular Play methods.
- a response parser to be applied to the response got after executing the request.
  The result will then contain the decoded error or success values
  (note that this can be the body enriched with data from headers/status code).

Example:

```scala
import sttp.tapir.*
import sttp.tapir.client.play.PlayClientInterpreter
import sttp.capabilities.pekko.PekkoStreams

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.ws.StandaloneWSClient

def example[I, E, O, R >: PekkoStreams](implicit wsClient: StandaloneWSClient): Unit = 
  val e: PublicEndpoint[I, E, O, R] = ???
  val inputArgs: I = ???

  val (req, responseParser) = PlayClientInterpreter()
      .toRequestThrowDecodeFailures(e, s"http://localhost:9000")
      .apply(inputArgs)

  val result: Future[Either[E, O]] = req
      .execute()
      .map(responseParser)
```

## Limitations

Multipart requests are not supported.

Streaming capabilities:
- only `PekkoStreams` is supported (resp. `AkkaStreams` for Play 2.9)
