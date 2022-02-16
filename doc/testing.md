# Testing

## White box testing

If you are unit testing your application you should stub all external services.

### Interpreter

If you are using tapir do describe your API and run using some server interpreter then you might want to test your
endpoints definitions (like validations, decoding) as well as your custom server options (like interceptors, failure handlers) while 
stubbing the actual response of your server logic.

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "@VERSION@"
```

Let's assume you are using Akka interpreter, then given the following endpoint:

```scala mdoc:invisible
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model.StatusCode
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint.Full
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.server.interceptor.exception.ExceptionContext
import sttp.tapir.server.interceptor.{CustomInterceptors, ValuedEndpointOutput}
import sttp.tapir.server.stub.TapirStubInterpreter
import scala.concurrent.Future
```

```scala mdoc:silent
val greetUser: Full[String, String, Unit, String, String, Any, Future] = endpoint.get
  .in("api" / "users" / "greet")
  .securityIn(auth.bearer[String]())
  .out(stringBody)
  .errorOut(stringBody)
  .serverSecurityLogic(token =>
    Future.successful {
      (if (token == "secret-password") Right("user123") else Left("unauthorized"))
    }
  )
  .serverLogic(user => _ => Future.successful(Right(s"hello $user")))
```

And the following `AkkaHttpServerOptions`:

```scala mdoc:silent
val options: CustomInterceptors[Future, AkkaHttpServerOptions] = AkkaHttpServerOptions.customInterceptors
  .exceptionHandler((ctx: ExceptionContext) =>
    Some(ValuedEndpointOutput(stringBody.and(statusCode), (s"failed due to ${ctx.e.getMessage}", StatusCode.InternalServerError)))
  )
```

You can write an interpreter stub for your endpoint. 
The `backend()` method returns `SttpBackend` which runs all requests against interpreter and can be used in tests:

```scala mdoc:silent
class MySpec extends AsyncFlatSpec with Matchers {
  
  val stub = TapirStubInterpreter[Future, Any, AkkaHttpServerOptions](options.serverLog(None), new FutureMonad())
    .forServerEndpoint(greetUser)
    .throwException(new RuntimeException("error"))
    .backend()

  it should "use my custom exception handler" in {
    sttp.client3.basicRequest
      .get(uri"http://test.com/api/users/greet")
      .send(stub)
      .map(_.body shouldBe Left("failed due to error"))  
  }
}
```

A stub which executes an endpoint's server logic can also be created:

```scala mdoc:silent
import scala.concurrent.ExecutionContext.Implicits.global

TapirStubInterpreter[Future, Any, AkkaHttpServerOptions](options.serverLog(None), new FutureMonad())
  .forServerEndpoint(greetUser).runLogic()
  .backend()
```

Please note that when passing `AkkaHttpServerOptions` to `TapirStubInterpreter` server logging was disabled `options.serverLog(None)`.

### External APIs described with Tapir

If you are using sttp client to send HTTP requests, and if the externals apis, 
which your application consumes, are described using tapir `Endpoint`s, you can create a stub of the service by converting 
endpoints to `SttpBackendStub` (see the [sttp documentation](https://sttp.softwaremill.com/en/latest/testing.html) for 
details on how the stub works).

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "@VERSION@"
```

And the following imports:

```scala mdoc:silent
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub._
``` 

Given the following endpoint:

```scala mdoc:silent
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

case class ResponseWrapper(value: Double)

val e = sttp.tapir.endpoint
  .in("api" / "sometest4")
  .in(query[Int]("amount"))
  .post
  .out(jsonBody[ResponseWrapper])
```

Convert any endpoint to `SttpBackendStub`:

```scala mdoc:silent
import sttp.client3.Identity
import sttp.client3.monad.IdMonad

val backend: SttpBackendStub[Identity, Any] = SttpBackendStub
  .apply(IdMonad)
  .whenRequestMatchesEndpoint(e)
  .thenSuccess(ResponseWrapper(1.0))
```

A stub which executes an endpoint's server logic can also be created (here with an identity effect, but any supported
effect can be used):

```scala mdoc:silent
import sttp.client3.Identity
import sttp.client3.monad.IdMonad

val anotherBackend: SttpBackendStub[Identity, Any] = SttpBackendStub
  .apply(IdMonad)
  .whenRequestMatchesEndpointThenLogic(e.serverLogic[Identity](_ => Right(ResponseWrapper(1.0))))
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
"com.softwaremill.sttp.tapir" %% "tapir-sttp-mock-server" % "@VERSION@"
```

Imports:

```scala mdoc:silent
import sttp.tapir.server.mockserver._
``` 

Then, given the following endpoint:

```scala mdoc:silent
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

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

## Shadowed endpoints

It is possible to define a list of endpoints where some endpoints will be overlapping with each other. In such
case when all matching requests will be handled by the first endpoint; the second endpoint will always be omitted. 
To detect such cases one can use `FindShadowedEndpoints` util class which takes an input of
type `List[AnyEndpoint]` an outputs `Set[ShadowedEndpoint]`.

Example 1:

```scala mdoc:silent
import sttp.tapir.testing.FindShadowedEndpoints

val e1 = endpoint.get.in("x" / paths)
val e2 = endpoint.get.in("x" / "y" / "x")
val e3 = endpoint.get.in("x")
val e4 = endpoint.get.in("y" / "x")
val res = FindShadowedEndpoints(List(e1, e2, e3, e4)) 
```

Results in:

```scala mdoc
res.toString
```

Example 2:

```scala mdoc:silent:nest
import sttp.tapir.testing.FindShadowedEndpoints

val e1 = endpoint.get.in(path[String].name("y_1") / path[String].name("y_2"))
val e2 = endpoint.get.in(path[String].name("y_3") / path[String].name("y_4"))
val res = FindShadowedEndpoints(List(e1, e2))
```

Results in:

```scala mdoc
res.toString
```

Note that the above takes into account only the method & the shape of the path. It does *not* take into account possible
decoding failures: these might impact request-endpoint matching, and the exact behavior is determined by the
[`DecodeFailureHandler`](server/errors.md#decode-failures) used.