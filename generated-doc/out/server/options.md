# Server options

Each interpreter can be configured using an options object, which includes:

* how to create a file (when receiving a response that is mapped to a file, or when reading a file-mapped multipart 
  part)
* if, and how to handle exceptions (see [error handling](errors.md))
* if, and how to log requests (see [logging & debugging](debugging.md))  
* how to handle decode failures (see [error handling](errors.md))
* additional user-provided [interceptors](interceptors.md)

To use custom server options pass them as an argument to the interpreter's `apply` method.
For example, for `PekkoHttpServerOptions` and `PekkoHttpServerInterpreter`:

```scala
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

val customDecodeFailureHandler: DecodeFailureHandler[Future] = ???

val customServerOptions: PekkoHttpServerOptions = PekkoHttpServerOptions
  .customiseInterceptors
  .decodeFailureHandler(customDecodeFailureHandler)
  .options

PekkoHttpServerInterpreter(customServerOptions)
```

## Hiding authenticated endpoints

By default, if authentication credentials are missing for an endpoint which defines [authentication inputs](../endpoint/security.md),
a `401 Unauthorized` response is returned.

If you would instead prefer to hide the fact that such an endpoint exists from the client, a `404 Not Found` can be 
returned instead by using a different decode failure handler. For example, using akka-http:

```scala
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

val customServerOptions: PekkoHttpServerOptions = PekkoHttpServerOptions
  .customiseInterceptors
  .decodeFailureHandler(DefaultDecodeFailureHandler.hideEndpointsWithAuth[Future])
  .options

PekkoHttpServerInterpreter(customServerOptions)
```

Note however, that it can still be possible to discover the existence of certain endpoints using timing attacks.
Moreover, any `400 Bad Request` response are also converted to a `404`, making working the endpoint harder - there's
no feedback as to what kind of query parameters or headers might be missing or malformed.

This applies only to inputs, which fail do decode. For scenarios where the inputs decode successfully, but the 
authentication should fail, an error result should be returned from the [security logic](logic.md).
