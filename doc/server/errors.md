# Error handling

Error handling in tapir is divided into three areas:

1. Error outputs: defined per-endpoint, used for errors handled by the business logic
2. Failed effects: exceptions which are not handled by the server logic (corresponds to 5xx responses)
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

```scala mdoc:compile-only
import sttp.tapir.*
import sttp.tapir.server.netty.NettyFutureServerInterpreter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.*

given ExecutionContext = ExecutionContext.global
type ErrorInfo = String
def logic(s: String): Future[Int] = ???

def handleErrors[T](f: Future[T]): Future[Either[ErrorInfo, T]] =
  f.transform {
    case Success(v) => Success(Right(v))
    case Failure(e) =>
      println(s"Exception when running endpoint logic: $e")
      Success(Left(e.getMessage))
  }

NettyFutureServerInterpreter().toRoute(
  endpoint
    .errorOut(plainBody[ErrorInfo])
    .out(plainBody[Int])
    .in(query[String]("name"))
    .serverLogic((logic _).andThen(handleErrors))
)
```

In the above example, errors are represented as `String`s (aliased to `ErrorInfo` for readability). When the
logic completes successfully an `Int` is returned. Any exceptions that are raised are logged, and represented as a
value of type `ErrorInfo`.

Following the convention, the left side of the `Either[ErrorInfo, T]` represents an error, and the right side success.

Alternatively, errors can be recovered from failed effects and mapped to the error output - provided that the `E` type
in the endpoint description is itself a subclass of exception. This can be done by using the `serverLogicRecoverErrors` 
to specify the server logic, see the dedicated [section](logic.md) for more information.

## Failed effects: unhandled exceptions

If the logic function, which is passed to the server interpreter, fails (i.e. throws an exception, which results in
a failed `Future` or `IO`/`Task`), this will be handled by the logging and exception interceptors. By default, an
`ERROR` will be logged, and an `500 InternalServerError` returned.

## Decode failures

Quite often user input will be malformed and decoding of the request will fail. Should the request be completed with a
`400 Bad Request` response, or should the request be forwarded to another endpoint? By default, tapir follows OpenAPI
conventions, that an endpoint is uniquely identified by the method and served path. That's why:

- a `405 Method Not Allowed` is returned if multiple endpoints have been interpreted, and for at least one of them
  the path matched, but the method didn't. We assume that all endpoints for that path have been given to the
  interpreter, hence the response. This behavior can be [customised or turned off](#custom-reject-interceptor) using the `RejectInterceptor`
- an "endpoint doesn't match" result is returned if the request method or path doesn't match. The http library should
  attempt to serve this request with the next endpoint. The path doesn't match if a path segment is missing, there's
  a constant value mismatch or a decoding error (e.g. parsing a segment to an `Int` fails)
- if an [authentication input](../endpoint/security.md) fails to decode, a `401 Unauthorized` is returned together with
  an appropriate `WWW-Authenticate` header. See [options](options.md) documentation on how to return a `404` instead,
  to hide the endpoint
- otherwise, we assume that this is the correct endpoint to serve the request, but the parameters are somehow
  malformed. A `400 Bad Request` response is returned if a query parameter, header or body causes any decode failure,
  or if the decoding a path capture causes a validation error. It is also the default result on path segment decoding failures,
  for example if parsing such a segment to an `Int` fails, or there's an incorrect Enumeratum Enum value passed.

The behavior described in the latter three points can be customised by providing a custom
`sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler` when creating the server options. This handler, basing on the request,
failing input and failure description can decide, whether to return a "no match" or a specific response.

Only the first failure encountered for a specific endpoint is passed to the `DecodeFailureHandler`. Inputs are decoded
in the following order: method, path, query, header, body.

Note that the decode failure handler is used **only** for failures that occur during decoding of path, query, body
and header parameters - while invoking `Codec.decode`. It does not handle any failures or exceptions that occur
when invoking the logic of the endpoint.

### Custom reject interceptor

The default reject interceptor can be customised by providing your own reject handler - a case class consisting of:

- a function to determine the response format (other than the default plain text),
- a default status code and message to be used if the rejected input was not the HTTP method.

Two default implementations of the reject handler are provided by the `DefaultRejectHandler`:

- `DefaultRejectHandler` - which returns a `405 Method Not Allowed` when the HTTP method was rejected, and otherwise 
  propagates the rejection to the server interpreter library,
- `DefaultRejectHandler.orNotFound` - similar, but returns a `404 Not Found` instead of propagating when the rejected 
  input was not the HTTP method.

### Default failure handler

The default decode failure handler is a case class, consisting of functions which decide whether to respond with
an error or return a "no match", create error messages and create the response. Parts of the default behavior can be
swapped, e.g. to return responses in a different format (other than plain text), or customise the error messages.

Moreover, when using the `DefaultDecodeFailureHandler`, decode failure handling can be overriden on a per-input/output
basis, by setting an attribute. For example:

```scala mdoc:compile-only
import sttp.tapir.*
// bringing into scope the onDecodeFailureNextEndpoint extension method
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.OnDecodeFailure.*

case class UserId(value: String)
object UserId:
  given Codec[String, UserId, CodecFormat.TextPlain] = Codec.string.mapDecode(raw =>
    UserId.make(raw) match {
      case Left(error) =>
        DecodeResult.Error(raw, new IllegalArgumentException(s"Invalid User value ($raw), failed with $error"))
      case Right(result) => DecodeResult.Value(result)
    })(_.value)

  def make(in: String): Either[String, UserId] =
    if (in.length > 5) Right(new UserId(in))
    else Left("Too short")

// If your codec for UserId fails, allow checking other endpoints for possible matches, like /customer/some_special_case
endpoint.in("customer" / path[UserId]("user_id").onDecodeFailureNextEndpoint)
// Another endpoint, tried after matching for the previous one fails on decoding of a UserId
endpoint.in("customer" / "some_special_case")
```

## Customising how error messages are rendered

To return error responses in a different format (other than plain text), you can customise both the exception, decode
failure and reject handlers individually, or use the `CustomiseInterceptors.defaultHandlers` method which customises the
default ones for you.

We'll need to provide both the endpoint output which should be used for error messages, along with the output's value:

```scala mdoc:compile-only
import sttp.tapir.*
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.server.netty.NettyFutureServerOptions
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

case class MyFailure(msg: String)
def myFailureResponse(m: String): ValuedEndpointOutput[_] =
  ValuedEndpointOutput(jsonBody[MyFailure], MyFailure(m))

val myServerOptions: NettyFutureServerOptions = NettyFutureServerOptions
  .customiseInterceptors
  .defaultHandlers(myFailureResponse)
  .options
```

If you want to customise anything beyond the rendering of the error message, or use non-default implementations of the
exception handler / decode failure handler, you'll still have to customise each by hand.

## Endpoints which cannot fail

In some cases, you can have endpoints which "cannot fail", that is which do not have any "expected" errors. For these scenarios, you can use `infallibleEndpoint` as a starting point (instead of `endpoint`). When using `infallibleEndpoint`, the error type is fixed to `Nothing`.

Of course, the server logic for such endpoints might still throw exceptions / return failed effects. Such failures are logged & intercepted in the usual way, as described above.