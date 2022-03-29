# Interceptors

## Request interceptors

Request interceptors intercept the whole request, and are called once for each request. They can provide additional
endpoint interceptors, as well as modify the request, server endpoints, or the response.

The following request interceptors are provided by default (and if enabled, called in this order):

* the [metrics interceptor](observability.md), which by default is disabled
* a CORS interceptor (disabled by default)
* the `RejectInterceptor`, which specifies what should be done when decoding the request has failed for all 
  interpreted endpoints. The default is to return a 405 (method not allowed), if there's at least one decode failure
  on the method, and a "no-match" otherwise (which is handled in an intereprter-specific manner)

Request interceptors for two common scenarios can be created using the `RequestInterceptor.transformServerRequest` and 
`RequestInterceptor.filterServerEndpoints` methods.

Note, that for most server interpreters, the server endpoints passed to the request interceptor will be pre-filtered
using `FilterServerEndpoints`, as a performance optimization (these will be only the endpoints for which the request
path might potentially decode successfully).
  
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

Using `customInterceptors` on the options companion object, it is possible to customise the built-in interceptors. New 
ones can be prepended to the interceptor stack using `.prependInterceptor`, or appended using `.appendInterceptor`. 
However, the decode failure interceptor is alwyas the last one. Customisation can include removing the interceptor 
altogether.