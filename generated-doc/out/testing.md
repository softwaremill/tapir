# Testing

## White box testing

If you are unit testing your application you should stub all external services.

If you are using sttp client to send HTTP requests, and if the externals apis, 
which yours application consumes, are described using tapir, you can create a stub of the service by converting 
endpoints to `SttpBackendStub` (see the [sttp documentation](https://sttp.softwaremill.com/en/latest/testing.html) for 
details on how the stub works).

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "0.18.0-M2"
```

And the following imports:

```scala
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub._
``` 

Given the following endpoint:

```scala
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

case class ResponseWrapper(value: Double)

val endpoint = sttp.tapir.endpoint
  .in("api" / "sometest4")
  .in(query[Int]("amount"))
  .post
  .out(jsonBody[ResponseWrapper])
```

Convert any endpoint to `SttpBackendStub`:

```scala
import sttp.client3.monad.IdMonad

implicit val backend = SttpBackendStub
  .apply(IdMonad)
  .whenRequestMatchesEndpoint(endpoint)
  .thenSuccess(ResponseWrapper(1.0))
```

## Black box testing

When testing application as a whole component, running for example in docker, you might want to stub external services
with which you application interacts, to steer their behaviors. 

To do that you might want to use well-known solutions like e.g. [wiremock](http://wiremock.org/) or [mock-server](https://www.mock-server.com/), 
but if their api is described using tapir you might want to use [livestub](https://github.com/softwaremill/livestub), which combines nicely with the rest of the sttp ecosystem.
