# Testing

## White box testing

If you are unit testing your application you should mock all external services to make your tests reliable.
If you are using sttp client and if externals apis, which yours application consumes, 
are described using tapir, you can do that with easy by simply converting endpoints to `SttpBackendStub`.

Add following import:

```scala
import sttp.tapir.server.stub._
``` 

And convert any endpoint to `SttpBackendStub`:
```scala
  val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest4")
      .in(query[Int]("amount"))
      .post
      .out(jsonBody[ResponseWrapper])

    implicit val backend = SttpBackendStub
      .apply(idMonad)
      .whenRequestMatches(endpoint)
      .thenSuccess(ResponseWrapper(1.0))
```

## Black box testing

When testing application as a whole component, running for example in docker, you might want to stub external services
with which you application interacts, to steer their behaviors. 

To do that you might want to use well-known solutions like e.g. [wiremock](http://wiremock.org/) or [mock-server](https://www.mock-server.com/), 
but if their api is described using tapir you might want to use [livestub](https://github.com/softwaremill/livestub), which combines nicely with the rest of the sttp ecosystem.
