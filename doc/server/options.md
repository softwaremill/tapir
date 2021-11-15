# Server options

Each interpreter can be configured using an options object, which includes:

* how to create a file (when receiving a response that is mapped to a file, or when reading a file-mapped multipart 
  part)
* if, and how to handle exceptions (see [error handling](errors.md))
* if, and how to log requests (see [logging & debugging](debugging.md))  
* how to handle decode failures (see [error handling](errors.md))
* additional user-provided interceptors

To use custom server options pass them as an argument to the interpreter's `apply` method.
For example, for `AkkaHttpServerOptions` and `AkkaHttpServerInterpreter`:

```scala mdoc:compile-only
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

val customDecodeFailureHandler: DecodeFailureHandler = ???

val customServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions
  .customInterceptors
  .decodeFailureHandler(customDecodeFailureHandler)
  .options
  
AkkaHttpServerInterpreter(customServerOptions)
```

## Hiding authenticated endpoints

By default, if authentication credentials are missing for an endpoint which defines [authentication inputs](../endpoint/security.md),
a `401 Unauthorized` response is returned.

If you would instead prefer to hide the fact that such an endpoint exists from the client, a `404 Not Found` can be 
returned instead by using a different decode failure handler. For example, using akka-http:

```scala mdoc:compile-only
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

val hidingDecodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handlerHideUnauthorized

val customServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions
  .customInterceptors
  .decodeFailureHandler(hidingDecodeFailureHandler)
  .options
  
AkkaHttpServerInterpreter(customServerOptions)
```

Note however, that it can still be possible to discover the existence of certain endpoints using timing attacks.
Moreover, any `400 Bad Request` response are also converted to a `404`, making working the endpoint harder - there's
no feedback as to what kind of query parameters or headers might be missing or malformed.

This applies only to inputs, which fail do decode. For scenarios where the inputs decode successfully, but the 
authentication should fail, an error result should be returned from the [security logic](logic.md).

## Request interceptors

Request interceptors intercept whole request, and are called once for each request. They can provide additional
endpoint interceptors, as well as modify the request, or the response.

The following request interceptors are provided by default (and if enabled, called in this order):

* the [metrics interceptor](observability.md), which by default is disabled
* the `RejectInterceptor`, which specifies what should be done when decoding the request has failed for all 
  interpreted endpoints. The default is to return a 405 (method not allowed), if there's at least one decode failure
  on the method, and a "no-match" otherwise (which is handled in an intereprter-specific manner)
  
## Endpoint interceptors

An `EndpointInterceptor` allows intercepting the handling of a request by an endpoint, when either the endpoint's inputs 
have been decoded successfully, or when decoding has failed.

The following interceptors are used by default, and if enabled, called in this order:

* exception interceptor 
* logging interceptor
* decode failure handler interceptor

Note that while the request will be passed top-to-bottom, handling of the result will be done in opposite order. 
E.g., if the result is a failed effect (an exception), it will first be logged by the logging interceptor, and 
only later passed to the exception interceptor.

Using `customInterceptors` on the options companion object, it is possible to customise the built-in interceptors, as 
well as add new ones in-between the default initial (exception, logging) and decode failure interceptors. Customisation 
can include removing the interceptor altogether. Later, interceptors can be added using `.prependInterceptor` and 
`.appendInterceptor`.
