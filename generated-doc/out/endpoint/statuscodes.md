# Status codes

## Arbitrary status codes

To provide a (varying) status code of a server response, use the `statusCode` output, which maps to a value of type
`sttp.model.StatusCode`. The companion object contains known status 
codes as constants. This type of output is used only when interpreting the endpoint as a server. If your endpoint returns varying status codes
which you would like to have listed in documentation use `statusCode.description(code1, "code1 description").description(code2, "code2 description")` output.

## Fixed status code

A fixed status code can be specified using the `statusCode(code)` output.

## Different outputs for status codes

It is also possible to specify how status codes correspond with different outputs. All mappings should have a common supertype,
which is also the type of the output. These mappings are used to determine the status code when interpreting an endpoint
as a server, as well as when generating documentation and to deserialise client responses to the appropriate type,
basing on the status code.

For example, below is a specification for an endpoint where the error output is a sealed trait `ErrorInfo`; 
such a specification can then be refined and reused for other endpoints:

```scala
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import sttp.model.StatusCode
import io.circe.generic.auto._

sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case class Unauthorized(realm: String) extends ErrorInfo
case class Unknown(code: Int, msg: String) extends ErrorInfo
case object NoContent extends ErrorInfo

// here we are defining an error output, but the same can be done for regular outputs
val baseEndpoint = endpoint.errorOut(
  oneOf[ErrorInfo](
    oneOfMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
    oneOfMapping(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
    oneOfMapping(StatusCode.NoContent, emptyOutputAs(NoContent)),
    oneOfDefaultMapping(jsonBody[Unknown].description("unknown"))
  )
)
```

Each mapping, defined using the `oneOfMapping` method is a case class, containing the output description as well as
the status code. Moreover, default mappings can be defined using `oneOfDefaultMapping`:

* for servers, the default status code for error outputs is `400`, and for normal outputs `200` (unless a `statusCode` 
  is used in the nested output)
* for clients, a default mapping is a catch-all. 

Both `oneOfMapping` and `oneOfDefaultMapping` return a value of type `OneOfMapping`. A list of these values can be
dynamically assembled (e.g. using a default set of cases, plus endpoint-specific mappings), and provided to `oneOf`.

## One-of-mapping and type erasure

Type erasure may prevent a one-of-mapping from working properly. The following example will fail at compile time because `Right[NotFound]` and `Right[BadRequest]` will become `Right[Any]`:

```scala
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import sttp.model.StatusCode
import io.circe.generic.auto._

case class ServerError(what: String)

sealed trait UserError
case class BadRequest(what: String) extends UserError
case class NotFound(what: String) extends UserError

val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    oneOfMapping(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")),
    oneOfMapping(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")),
    oneOfMapping(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")),
  )
)
// error: Constructing oneOfMapping of type scala.util.Right[repl.MdocSession.App.ServerError,repl.MdocSession.App.NotFound] is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify that the input matches the desired class. Please use oneOfMappingClassMatcher, oneOfMappingValueMatcher or oneOfMappingFromMatchType instead
//     oneOfMappingValueMatcher(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")) {
//                             ^
// error: Constructing oneOfMapping of type scala.util.Right[repl.MdocSession.App.ServerError,repl.MdocSession.App.BadRequest] is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify that the input matches the desired class. Please use oneOfMappingClassMatcher, oneOfMappingValueMatcher or oneOfMappingFromMatchType instead
//     oneOfMappingValueMatcher(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")) {
//                             ^
// error: Constructing oneOfMapping of type scala.util.Left[repl.MdocSession.App.ServerError,repl.MdocSession.App.UserError] is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify that the input matches the desired class. Please use oneOfMappingClassMatcher, oneOfMappingValueMatcher or oneOfMappingFromMatchType instead
//     oneOfMappingValueMatcher(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")) {
//                             ^
```

The solution is therefore to handwrite a function checking that a value (of type `Any`) is of the correct type:


```scala
val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    oneOfMappingValueMatcher(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")) {
      case Right(NotFound(_)) => true
    },
    oneOfMappingValueMatcher(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")) {
      case Right(BadRequest(_)) => true
    },
    oneOfMappingValueMatcher(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")) {
      case Left(ServerError(_)) => true
    }
  )
)
```

Of course, you could use `oneOfMappingValueMatcher` to do runtime filtering for other purpose than solving type erasure.

In the case of solving type erasure, writing by hand partial function to match value against composition of case class and sealed trait can be repetitive.
To make that more easy, we provide an **experimental** typeclass - `MatchType` - so you can automatically derive that partial function:

```scala
import sttp.tapir.typelevel.MatchType

val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    oneOfMappingFromMatchType(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")),
    oneOfMappingFromMatchType(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")),
    oneOfMappingFromMatchType(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized"))
  )
)
```

## Server interpreters

Unless specified otherwise, successful responses are returned with the `200 OK` status code, and errors with 
`400 Bad Request`. For exception and decode failure handling, see [error handling](../server/errors.md).

## Next

Read on about [codecs](codecs.md).
