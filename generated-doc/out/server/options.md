# Server options

Each interpreter accepts an implicit options value, which contains configuration including:

* how to create a file (when receiving a response that is mapped to a file, or when reading a file-mapped multipart 
  part)
* if, and how to handle exceptions (see [error handling](errors.md))
* if, and how to log requests (see [loggin & debugging](debugging.md))  
* how to handle decode failures (see [error handling](errors.md))
* additional user-provided interceptors

To use custom server options, define an implicit value, which will be in scope when converting endpoints to the
target route representation. For example, for `AkkaHttpServerOptions`:

```scala
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions

implicit val customServerOptions: AkkaHttpServerOptions = 
  AkkaHttpServerOptions.customInterceptors(decodeFailureHandler = ???)
```

## Request interceptors

Request interceptors intercept whole request, and are called once for each request. They can provide additional
endpoint interceptors, as well as modify the request, or the response.

## Endpoint interceptors

An `EndpointInterceptor` allows intercepting the handling of a request by an endpoint, when either the endpoint's inputs 
have been decoded successfully, or when decoding has failed.

There are three interceptors that are used by default, which are called in this order:

1. exception interceptor 
2. logging interceptor
3. decode failure handler interceptor

Note that while the request will be passed top-to-bottom (1->3), handling of the result will be done in opposite order 
(3->1). E.g., if the result is a failed effect (an exception), it will first be logged by the logging interceptor, and 
only later passed to the exception interceptor.

Using `customInterceptors` on the options companion object, it is possible to customise the built-in interceptors, as 
well as add new ones in-between the default initial (exception, logging) and decode failure interceptors. Customisation 
can include removing the interceptor altogether. Later, interceptors can be added using `.prependInterceptor` and 
`.appendInterceptor`.
