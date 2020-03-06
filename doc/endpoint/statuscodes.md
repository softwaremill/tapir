# Status codes

To provide a (varying) status code of a server response, use the `statusCode` output, which maps to a value of type
`type tapir.model.StatusCode` (which is an alias for `Int`). The `tapir.model.StatusCodes` object contains known status 
codes as constants. This type of output is used only when interpreting the endpoint as a server. If your endpoint returns varying status codes
which you would like to have listed in documentation use `statusCode.description(code1, "code1 description").description(code2, "code2 description")` output.

Alternatively, a fixed status code can be specified using the `statusCode(code)` output.

## Dynamic status codes

It is also possible to specify how status codes map to different outputs. All mappings should have a common supertype,
which is also the type of the output. These mappings are used to determine the status code when interpreting an endpoint
as a server, as well as when generating documentation and to deserialise client responses to the appropriate type,
basing on the status code.

For example, below is a specification for an endpoint where the error output is a sealed trait `ErrorInfo`; 
such a specification can then be refined and reused for other endpoints:

```scala
sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case class Unauthorized(realm: String) extends ErrorInfo
case class Unknown(code: Int, msg: String) extends ErrorInfo
case object NoContent extends ErrorInfo

val baseEndpoint = endpoint.errorOut(
  oneOf(
    statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
    statusMapping(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
    statusMapping(StatusCode.NoContent, emptyOutput.map(_ => NoContent)(_ => ())),
    statusDefaultMapping(jsonBody[Unknown].description("unknown"))
  )
)
```

Each mapping, defined using the `statusMapping` method is a case class, containing the output description as well as
the status code. Moreover, default mappings can be defined using `statusDefaultMapping`:

* for servers, the default status code for error outputs is `400`, and for normal outputs `200` (unless a `statusCode` 
  is used in the nested output)
* for clients, a default mapping is a catch-all. 

Both `statusMapping` and `statusDefaultMapping` return a value of type `StatusMapping`. A list of these values can be
dynamically assembled (e.g. using a default set of cases, plus endpoint-specific mappings), and provided to `oneOf`.

## Status mapping and type erasure

Sometime at runtime status mapping resolution can not work properly because of type erasure.
For example this code will fail at compile time; because of type erasure `Right[NotFound]` and `Right[BadRequest]` will 
become `Right[Any]`, therefore the code would not be able to find the correct mapping for a value:

```scala
case class ServerError(what: String)

sealed trait UserError
case class BadRequest(what: String) extends UserError
case class NotFound(what: String) extends UserError

val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    statusMapping(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")),
    statusMapping(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")),
    statusMapping(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")),
  )
)
```

The solution is therefore to handwrite a function checking that a `val` (of type `Any`) is of the correct type:

```
val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    statusMappingValueMatcher(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")) {
      case Right(NotFound(_)) => true
    },
    statusMappingValueMatcher(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")) {
      case Right(BadRequest(_)) => true
    },
    statusMappingValueMatcher(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")) {
      case Left(ServerError(_)) => true
    }
  )
)
```

Of course you could use `statusMappingValueMatcher` to do runtime filtering for other purpose than solving type erasure.

In the case of solving type erasure, writing by hand partial function to match value against composition of case class and sealed trait can be repetitive.
To make that more easy, we provide an **experimental** typeclass - `MatchType` - so you can automatically derive that partial function:
```
import sttp.tapir.typelevel.MatchType

val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    statusMappingFromMatchType(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")),
    statusMappingFromMatchType(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")),
    statusMappingFromMatchType(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized"))
  )
)
```

## Server interpreters

Unless specified otherwise, successful responses are returned with the `200 OK` status code, and errors with 
`400 Bad Request`. For exception and decode failure handling, see [error handling](../server/errors.html).

## Next

Read on about [codecs](codecs.html).
