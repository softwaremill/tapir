# Server options

Each interpreter accepts an implicit options value, which contains configuration including:

* how to create a file (when receiving a response that is mapped to a file, or when reading a file-mapped multipart 
  part)
* how to handle decode failures (see also [error handling](errors.md))
* debug-logging of request handling
* additional user-provided endpoint interceptors

To customise the server options, define an implicit value, which will be visible when converting an endpoint or multiple
endpoints to a route/routes. For example, for `AkkaHttpServerOptions`:

```scala mdoc:compile-only
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions

implicit val customServerOptions: AkkaHttpServerOptions = 
  AkkaHttpServerOptions.customInterceptors(decodeFailureHandler = ???)
```

## Endpoint interceptors

An `EndpointInterceptor` allows intercepting the handling of a request by an endpoint, when either the endpoint's inputs 
have been decoded successfully, or when decoding has failed.

There are two interceptors that are used by default: for [handling decode failures](errors.md), and for [logging](debugging.md).
These can be customised, using the `customInterceptors` method on the options companion object (e.g. `AkkaHttpServerOptions.customInterceptors`).
Customisation can include removing the interceptor altogether. Custom interceptors might be added, either in-between
logging and handling decode failures, or before/after these.