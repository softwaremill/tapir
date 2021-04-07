# Error handling

Error handling in tapir is divided into three areas:

1. Error outputs: defined per-endpoint, used for errors handled by the business logic
2. Failed effects: exceptions which are not handled by the business logic (corresponds to 5xx responses)    
3. Decode failures: format errors, when the input values can't be decoded (corresponds to 4xx responses, or 
   trying another endpoint)

While 1. is specific to an endpoint, handlers for 2. and 3. are typically the same for multiple endpoints, and are
specified as part of the server's interpreter [options](options.md).

## Error outputs

Each endpoint can contain dedicated error outputs, in addition to outputs which are used in case of success. The 
business logic can then return either an error value, or a success value. Any business-logic-level errors should
be signalled this way. This can include validation, failure of downstream services, or inability to serve the request
at that time.

If the business logic signals errors as exceptions, some or all can be recovered from and mapped to an error value.
For example:

```scala
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import scala.concurrent.Future
import scala.util._

implicit val ec = scala.concurrent.ExecutionContext.global
type ErrorInfo = String
def logic(s: String): Future[Int] = ???

def handleErrors[T](f: Future[T]): Future[Either[ErrorInfo, T]] =
  f.transform {
    case Success(v) => Success(Right(v))
    case Failure(e) =>
      println(s"Exception when running endpoint logic: $e")
      Success(Left(e.getMessage))
  }

AkkaHttpServerInterpreter.toRoute(
  endpoint
    .errorOut(plainBody[ErrorInfo])
    .out(plainBody[Int])
    .in(query[String]("name"))) {
  (logic _).andThen(handleErrors)
}
```

In the above example, errors are represented as `String`s (aliased to `ErrorInfo` for readability). When the
logic completes successfully an `Int` is returned. Any exceptions that are raised are logged, and represented as a
value of type `ErrorInfo`. 

Following the convention, the left side of the `Either[ErrorInfo, T]` represents an error, and the right side success.

Alternatively, errors can be recovered from failed effects and mapped to the error output - provided that the `E` type
in the endpoint description is itself a subclass of exception. This can be done using the `toRouteRecoverErrors` method
(or similar for other interpreters).

## Failed effects: unhandled exceptions

If the logic function, which is passed to the server interpreter, fails (i.e. throws an exception, which results in
a failed `Future` or `IO`/`Task`), this will be handled by the logging and exception interceptors. By default, an 
`ERROR` will be logged, and an `500 InternalServerError` returned.

## Decode failures

Quite often user input will be malformed and decoding of the request will fail. Should the request be completed with a 
`400 Bad Request` response, or should the request be forwarded to another endpoint? By default, tapir follows OpenAPI 
conventions, that an endpoint is uniquely identified by the method and served path. That's why:

* an "endpoint doesn't match" result is returned if the request method or path doesn't match. The http library should
  attempt to serve this request with the next endpoint. The path doesn't match if a path segment is missing, there's
  a constant value mismatch or a decoding error (e.g. parsing a segment to an `Int` fails)
* otherwise, we assume that this is the correct endpoint to serve the request, but the parameters are somehow 
  malformed. A `400 Bad Request` response is returned if a query parameter, header or body causes any decode failure, 
  or if the decoding a path capture causes a validation error.

This can be customised by providing an implicit instance of `sttp.tapir.server.DecodeFailureHandler`, which basing on 
the request, failing input and failure description can decide, whether to return a "no match" or a specific response.

Only the first failure is passed to the `DecodeFailureHandler`. Inputs are decoded in the following order: method, 
path, query, header, body.

Note that the decode failure handler is used **only** for failures that occur during decoding of path, query, body
and header parameters - while invoking `Codec.decode`. It does not handle any failures or exceptions that occur
when invoking the logic of the endpoint.

### Default failure handler

The default decode failure handler is a case class, consisting of functions which decide whether to respond with
an error or return a "no match", create error messages and create the response. 
 
To reuse the existing default logic, parts of the default behavior can be swapped, e.g. to return responses in 
a different format (other than textual):

```scala
import sttp.tapir._
import sttp.tapir.server._
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.model.{Header, StatusCode}
import io.circe.generic.auto._

implicit val ec = scala.concurrent.ExecutionContext.global
case class MyFailure(msg: String)
def myFailureResponse(c: StatusCode, hs: List[Header], m: String): ValuedEndpointOutput[_] =
  ValuedEndpointOutput(statusCode.and(headers).and(jsonBody[MyFailure]), (c, hs, MyFailure(m)))
  
val myDecodeFailureHandler = DefaultDecodeFailureHandler.handler.copy(
  response = myFailureResponse
)

implicit val myServerOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.customInterceptors(
  decodeFailureHandler = myDecodeFailureHandler
)
```

Note that when specifying that a response should be returned upon a failure, we need to provide the endpoint output 
which should be used to create the response, as well as a value for this output.

The default decode failure handler also has the option to return a `400 Bad Request`, instead of a no-match (ultimately
leading to a `404 Not Found`), when the "shape" of the path matches (that is, the number of segments in the request
and endpoint's paths are the same), but when decoding some part of the path ends in an error. See the
`badRequestOnPathErrorIfPathShapeMatches` in `ServerDefaults`.

Finally, you can provide custom error messages for validation errors (which optionally describe what failed) and 
failure errors (which describe the source of the error).

A completely custom implementation of the `DecodeFailureHandler` can also be used.
