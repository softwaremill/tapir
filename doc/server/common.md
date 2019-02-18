# Common server configuration

## Status codes

By default, successful responses are returned with the `200 OK` status code, and errors with `400 Bad Request`. However,
this can be customised when interpreting an endpoint as a directive/route, by providing implicit values of 
`type StatusMapper[T] = T => StatusCode`, where `type StatusCode = Int`.

This can be especially useful for error responses, in which case having an `Endpoint[I, E, O, S]`, you'd need to provide
an implicit `StatusMapper[E]`.
  
## Server options

Each interpreter accepts an implicit options value, which contains configuration values for:

* how to create a file (when receiving a response that is mapped to a file, or when reading a file-mapped multipart 
  part)
* how to handle decode failures  
  
### Handling decode failures

Quite often user input will be malformed and decoding will fail. Should the request be completed with a 
`400 Bad Request` response, or should the request be forwarded to another endpoint? By default, tapir follows OpenAPI 
conventions, that an endpoint is uniquely identified by the method and served path. That's why:

* an "endpoint doesn't match" result is returned if the request method or path doesn't match. The http library should
  attempt to serve this request with the next endpoint.
* otherwise, we assume that this is the correct endpoint to serve the request, but the parameters are somehow 
  malformed. A `400 Bad Request` response is returned if a query parameter, header or body is missing / decoding fails, 
  or if the decoding a path capture fails with an error (but not a "missing" decode result).

This can be customised by providing an implicit instance of `tapir.server.DecodeFailureHandler`, which basing on the 
request,  failing input and failure description can decide, whether to return a "no match", an endpoint-specific error 
value,  or a specific response.

Only the first failure is passed to the `DecodeFailureHandler`. Inputs are decoded in the following order: method, 
path, query, header, body.
