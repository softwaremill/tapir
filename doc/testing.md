# Testing

## Server endpoints

If you are exposing endpoints using one of the server interpreters, you might want to test a complete server endpoint,
how validations, built-in and custom [interceptors](server/interceptors.md) and [error handling](server/errors.md) 
behaves. This might be done while providing alternate, or using the original [server logic](server/logic.md).

Such testing is possible by creating a special [sttp client](https://sttp.softwaremill.com) backend. When a request
is sent using such a backend, no network traffic is happening. Instead, the request is decoded using the provided
endpoints, the appropriate logic is run, and then the response is encoded - as in a real server interpreter. But
similar as with a request, the response isn't sent over the network, but returned directly to the caller. Hence, 
binding to an interface isn't necessary to run these tests.

You can define the sttp requests by hand, to see how an arbitrary request will be handled by your server endpoints.
Or, you can interpret an endpoint as a [client](client/sttp.md), to test both how the client & server interpreters
interact with your endpoints.

The special backend that is described above is based on a `SttpBackendStub`, which can be used to stub arbitrary
behaviors. See the [sttp documentation](https://sttp.softwaremill.com/en/latest/testing.html) for details.

Tapir builds upon the `SttpBackendStub` to enable stubbing using `Endpoint`s or `ServerEndpoint`s. To start, add the 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "@VERSION@"
```

Let's assume you are using the [pekko http](server/pekkohttp.md) interpreter. Given the following server endpoint:

```scala mdoc:silent
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future

val someEndpoint: Endpoint[String, Unit, String, String, Any] = endpoint.get
  .in("api")
  .securityIn(auth.bearer[String]())
  .out(stringBody)
  .errorOut(stringBody)
  
val someServerEndpoint: ServerEndpoint[Any, Future] = someEndpoint
  .serverSecurityLogic(token =>
    Future.successful {
      if (token == "password") Right("user123") else Left("unauthorized")
    }
  )
  .serverLogic(user => _ => Future.successful(Right(s"hello $user")))  
```

A test which verifies how this endpoint behaves when interpreter as a server might look as follows:

```scala mdoc:silent
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.*
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub.TapirStubInterpreter

class MySpec extends AsyncFlatSpec with Matchers:
  it should "work" in {
    // given
    val backendStub: SttpBackend[Future, Any] = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(someServerEndpoint)
      .thenRunLogic()
      .backend()
      
    // when
    val response = basicRequest
      .get(uri"http://test.com/api/users/greet")
      .header("Authorization", "Bearer password")
      .send(backendStub)
      
    // then
    response.map(_.body shouldBe Right("hello user123"))  
  }
```

The `.backend` method creates the enriched `SttpBackendStub`, using the provided server endpoints and their
behaviors. Any requests will be handled by a stub server interpreter, using the complete request handling logic.

Projects generated using [adopt-tapir](https://adopt-tapir.softwaremill.com) include a test which uses the above approach.

### Custom interpreters

Custom interpreters can be provided to the stub. For example, to test custom exception handling, we might have the
following customised pekko http options:

```scala mdoc:silent
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.model.StatusCode

val exceptionHandler = ExceptionHandler.pure[Future](ctx =>
    Some(ValuedEndpointOutput(
      stringBody.and(statusCode),
      (s"failed due to ${ctx.e.getMessage}", StatusCode.InternalServerError)
    ))
)

val customOptions: CustomiseInterceptors[Future, PekkoHttpServerOptions] = {
  import scala.concurrent.ExecutionContext.Implicits.global
  PekkoHttpServerOptions.customiseInterceptors
    .exceptionHandler(exceptionHandler)
}    
```

Testing such an interceptor requires simulating an exception being thrown in the server logic:

```scala mdoc:silent
class MySpec2 extends AsyncFlatSpec with Matchers:
  it should "use my custom exception handler" in {
    // given
    val stub = TapirStubInterpreter(customOptions, SttpBackendStub.asynchronousFuture)
      .whenEndpoint(someEndpoint)
      .thenThrowException(new RuntimeException("error"))
      .backend()
      
    // when  
    sttp.client3.basicRequest
      .get(uri"http://test.com/api")
      .send(stub)
      // then
      .map(_.body shouldBe Left("failed due to error"))  
  }
```

Note that to provide alternate success/error outputs given a `ServerEndpoint`, the endpoint will have to be typed
using the full type information, that is using the `ServerEndpoint.Full` alias.

## External APIs

If you are integrating with an external API, which is described using tapir's `Endpoint`s, or if you'd like to create
an [sttp client stub backend](https://sttp.softwaremill.com/en/latest/testing.html), with arbitrary behavior for 
requests matching an endpoint, you can use the tapir `SttpBackendStub` extension methods.

Similarly as when testing server interpreters, add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "@VERSION@"
```

And the following imports:

```scala mdoc:silent
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub.*
``` 

Then, given the following endpoint:

```scala mdoc:silent
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

case class ResponseWrapper(value: Double)

val e = sttp.tapir.endpoint
  .in("api" / "sometest4")
  .in(query[Int]("amount"))
  .post
  .out(jsonBody[ResponseWrapper])
```

Any endpoint can be converted to `SttpBackendStub`:

```scala mdoc:silent
import sttp.client3.Identity

val backend: SttpBackendStub[Identity, Any] = SttpBackendStub
  .synchronous
  .whenRequestMatchesEndpoint(e)
  .thenSuccess(ResponseWrapper(1.0))
```

## Black box testing

When testing an application as a whole component, running for example in docker, you might want to stub external services
with which your application interacts. 

To do that you might want to use well-known solutions like e.g. [wiremock](http://wiremock.org/) or [mock-server](https://www.mock-server.com/), 
but if their api is described using tapir you might want to use [livestub](https://github.com/softwaremill/livestub), which combines nicely with the rest of the sttp ecosystem.

### Black box testing with mock-server integration

If you are writing integration tests for your application which communicates with some external systems  
(e.g payment providers, SMS providers, etc.), you could stub them using tapir's integration
with [mock-server](https://www.mock-server.com/)

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "sttp-mock-server" % "@VERSION@"
```

Imports:

```scala mdoc:silent
import sttp.tapir.server.mockserver.*
``` 

Then, given the following endpoint:

```scala mdoc:silent
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

case class SampleIn(name: String, age: Int)

case class SampleOut(greeting: String)

val sampleJsonEndpoint = endpoint.post
  .in("api" / "v1" / "json")
  .in(header[String]("X-RequestId"))
  .in(jsonBody[SampleIn])
  .errorOut(stringBody)
  .out(jsonBody[SampleOut])
```

and having any `SttpBackend` instance (for example, `TryHttpURLConnectionBackend` or with any other, arbitrary effect 
`F[_]` type), convert any endpoint to a **mock-server** expectation:

```scala mdoc:compile-only
import sttp.client3.{TryHttpURLConnectionBackend, UriContext}

val testingBackend = TryHttpURLConnectionBackend()
val mockServerClient = SttpMockServerClient(baseUri = uri"http://localhost:1080", testingBackend)

val in = "request-id-123" -> SampleIn("John", 23)
val out = SampleOut("Hello, John!")

val expectation = mockServerClient
  .whenInputMatches(sampleJsonEndpoint)((), in)
  .thenSuccess(out)
  .get
```

Then you can try to send requests to the mock-server as you would do with live integration:

```scala mdoc:compile-only
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.client3.{TryHttpURLConnectionBackend, UriContext}

val testingBackend = TryHttpURLConnectionBackend()
val in = "request-id-123" -> SampleIn("John", 23)
val out = SampleOut("Hello, John!")

val result = SttpClientInterpreter()
  .toRequest(sampleJsonEndpoint, baseUri = Some(uri"http://localhost:1080"))
  .apply(in)
  .send(testingBackend)
  .get
  
result == out 
```

## Endpoints verification

To use, add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-testing" % "@VERSION@"
```

### Shadowed endpoints

It is possible to define a list of endpoints where some endpoints will be overlapping with each other. In such
case when all matching requests will be handled by the first endpoint; the second endpoint will always be omitted. 
To detect such cases one can use `EndpointVerifier` util class which takes an input of
type `List[AnyEndpoint]` an outputs `Set[EndpointVerificationError]`.

Example 1:

```scala mdoc:silent
import sttp.tapir.testing.EndpointVerifier

val e1 = endpoint.get.in("x" / paths)
val e2 = endpoint.get.in("x" / "y" / "x")
val e3 = endpoint.get.in("x")
val e4 = endpoint.get.in("y" / "x")
val res = EndpointVerifier(List(e1, e2, e3, e4)) 
```

Results in:

```scala mdoc
res.toString
```

Example 2:

```scala mdoc:silent:nest
import sttp.tapir.testing.EndpointVerifier

val e1 = endpoint.get.in(path[String].name("y_1") / path[String].name("y_2"))
val e2 = endpoint.get.in(path[String].name("y_3") / path[String].name("y_4"))
val res = EndpointVerifier(List(e1, e2))
```

Results in:

```scala mdoc
res.toString
```

Note that the above takes into account only the method & the shape of the path. It does *not* take into account possible
decoding failures: these might impact request-endpoint matching, and the exact behavior is determined by the
[`DecodeFailureHandler`](server/errors.html#decode-failures) used.

### Incorrect path at endpoint

It is possible to define an endpoint where some part of an input will consume whole remaining path. That case can 
lead to situation where all other inputs defined after `paths` wildcard segment are omitted. To detect such cases one
can use `EndpointVerifier` util class which takes an input of
type `List[AnyEndpoint]` an outputs `Set[EndpointVerificationError]`.

Example 1:

```scala mdoc:silent
import sttp.tapir.testing.EndpointVerifier

val e = endpoint.options.in("a" / "b" / "c").securityIn("x" / "y" / paths)
val result = EndpointVerifier(List(e))
```

Results in:

```scala mdoc
result.toString
```

### Duplicated method definitions at endpoint

It is possible to define an endpoint where there are methods multiple times defined. To detect such cases one can use 
`EndpointVerifier` util class which takes an input of type `List[AnyEndpoint]` an outputs `Set[EndpointVerificationError]`.

Example 1:

```scala mdoc:silent
import sttp.tapir.testing.EndpointVerifier

val ep = endpoint.options.in("a" / "b" / "c").get
val result2 = EndpointVerifier(List(ep))
```

Results in:

```scala mdoc
result2.toString
```

### Duplicated endpoint names

Duplicate endpoint names will generate duplicate operation ids, when generating OpenAPI or AsyncAPI documentation. As
the operation ids should be unique, this is reported as an error:

Example 1:

```scala mdoc:silent
import sttp.tapir.testing.EndpointVerifier

val ep1 = endpoint.name("e1").get.in("a")
val ep2 = endpoint.name("e1").get.in("b")
val result3 = EndpointVerifier(List(ep1, ep2))
```

Results in:

```scala mdoc
result3.toString
```

## OpenAPI schema compatibility

The `OpenAPIVerifier` provides utilities for verifying that client and server endpoints are consistent with an OpenAPI specification. This ensures that endpoints defined in your code correspond to those documented in the OpenAPI schema, and vice versa.

To use the `OpenAPIVerifier`, add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-openapi-verifier" % "@VERSION@"
```

The `OpenAPIVerifier` supports two key verification scenarios:

1. **Server Verification**: Ensures that all endpoints defined in the OpenAPI specification are implemented by the server.
2. **Client Verification**: Ensures that the client implementation matches the OpenAPI specification.

As a result, you get a list of issues that describe the incomapatibilities, or an empty list, if the endpoints and schema are compatible.

### Example Usage

#### Server Endpoint Verification

```scala mdoc:silent
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIVerifier
import sttp.tapir.json.circe.*

val clientOpenAPISpecification: String = """
openapi: 3.0.0
info:
  title: Sample API
  version: 1.0.0
paths:
  /users:
    get:
      summary: Get users
      responses:
        "200":
          description: A list of users
          content:
            application/json:
              schema:
                type: array
                items:
                  type: string
"""

val serverEndpoints = List(
  endpoint.get.in("users").out(jsonBody[List[String]])
)

val serverIssues = OpenAPIVerifier.verifyServer(serverEndpoints, clientOpenAPISpecification)
```

#### Client Endpoint Verification

```scala mdoc:silent
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIVerifier
import sttp.tapir.json.circe.*

val serverOpenAPISpecification: String = """
openapi: 3.0.0
info:
  title: Sample API
  version: 1.0.0
paths:
  /users:
    get:
      summary: Get users
      responses:
        "200":
          description: A list of users
          content:
            application/json:
              schema:
                type: array
                items:
                  type: string
""".stripMargin

val clientEndpoints = List(
  endpoint.get.in("users").out(jsonBody[List[String]])
)

val clientIssues = OpenAPIVerifier.verifyClient(clientEndpoints, serverOpenAPISpecification)
```
