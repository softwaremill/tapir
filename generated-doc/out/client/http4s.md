# Using as an http4s client

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % "1.11.8"
```

To interpret an endpoint definition as an `org.http4s.Request[F]`, import:

```scala
import sttp.tapir.client.http4s.Http4sClientInterpreter
```

This objects contains four methods:
- `toRequestThrowDecodeFailures(PublicEndpoint, Option[Uri])` and `toSecureRequestThrowDecodeFailures(Endpoint, Option[Uri])`: 
  given the optional base URI, returns a function which generates a request and a response parser from endpoint inputs. Response parser throws
  an exception if decoding of the result fails.
  ```scala
  I => (org.http4s.Request[F], org.http4s.Response[F] => F[Either[E, O]])
  ```
- `toRequest(PublicEndpoint, Option[Uri])` and `toSecureRequest(Endpoint, Option[Uri])`: given the optional base URI, returns a function
  which generates a request and a response parser from endpoint inputs. Response parser
  returns an instance of `DecodeResult` which contains the decoded response body or error details.
  ```scala
  I => (org.http4s.Request[F], org.http4s.Response[F] => F[DecodeResult[Either[E, O]]])
  ```

Note that the returned functions have one argument each: first the security inputs (if any), and regular input values of the endpoint. This might 
be a single type, a tuple, or a case class, depending on the endpoint description.

After providing the input parameters, the following values are returned:
- An instance of `org.http4s.Request[F]` with the input value
  encoded as appropriate request parameters: path, query, headers and body.
  The request can be further customised and sent using an http4s client, or run against `org.http4s.HttpRoutes[F]`.
- A response parser to be applied to the response received after executing the request.
  The result will then contain the decoded error or success values
  (note that this can be the body enriched with data from headers/status code).

See the [runnable example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/client/Http4sClientExample.scala)
for example usage.

## Limitations

- Multipart requests are not supported yet.
- WebSockets are not supported yet.
- Streaming capabilities:
  - only `Fs2Streams` are supported at the moment.
