# Using as a Play client

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-play-client" % "0.17.0-M11+25-8d0263ff+20201216-0826-SNAPSHOT"
```

To make requests using an endpoint definition using the [play client](https://github.com/playframework/play-ws), import:

```scala
import sttp.tapir.client.play._
```

This adds the two extension methods to any `Endpoint`:
 - `toPlayRequestUnsafe(String)`: given the base URI returns a function,
   which will generate a request and a response parser which might throw
   an exception when decoding of the result fails
   ```scala
   I => (StandaloneWSRequest, StandaloneWSResponse => Either[E, O])
   ```
 - `toPlayRequest(String)`: given the base URI returns a function,
   which will generate a request and a response parser which represents
   decoding errors as the `DecodeResult` class
   ```scala
   I => (StandaloneWSRequest, StandaloneWSResponse => DecodeResult[Either[E, O]])
   ```

Note that these are a one-argument functions, where the single argument is the input of end endpoint. This might be a 
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
import sttp.tapir._
import sttp.tapir.client.play._
import sttp.capabilities.akka.AkkaStreams

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.ws.StandaloneWSClient

def example[I, E, O, R >: AkkaStreams](implicit wsClient: StandaloneWSClient) {
  val e: Endpoint[I, E, O, R] = ???
  val inputArgs: I = ???
  
  val (req, responseParser) = e
      .toPlayRequestUnsafe(s"http://localhost:9000")
      .apply(inputArgs)
  
  val result: Future[Either[E, O]] = req
      .execute()
      .map(responseParser)
}
```

## Limitations

Multipart requests are not supported.

Streaming capabilities:
- only `AkkaStreams` is supported
