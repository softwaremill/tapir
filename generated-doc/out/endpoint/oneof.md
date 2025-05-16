# One-of variants

There are two kind of one-of inputs/outputs:

* `oneOf` outputs where the arbitrary-output variants can represent different content using different outputs, and
* `oneOfBody` input/output where the body-only variants represent the same content, but with different content types

```{note}
`oneOf` and `oneOfBody` outputs are not related to `oneOf:` schemas when 
[generating](https://tapir.softwaremill.com/en/latest/docs/openapi.html) OpenAPI documentation.

Such schemas are generated for coproducts - e.g. `sealed trait` families - given an appropriate codec. See the
documentation on [coproducts](https://tapir.softwaremill.com/en/latest/endpoint/schemas.html#sealed-traits-coproducts) 
for details.
```

## `oneOf` outputs

Outputs with multiple variants can be specified using the `oneOf` output. Each variant is defined using a one-of 
variant. All possible outputs must have a common supertype. Typically, the supertype is a sealed trait, and the variants 
are implementing case classes.

Each one-of variant needs an `appliesTo` function to determine at run-time if the variant should be used for a given 
value. This function is inferred at compile time when using `oneOfVariant`, but can also be provided by hand, or if
the compile-time inference fails, using one of the other factory methods (see below). A catch-all variant can be defined
using `oneOfDefaultVariant`, and should be placed as the last variant in the list of possible variants.

When encoding such an output to a response, the first matching output is chosen, using the following rules:
1. the variants `appliesTo` method, applied to the output value (as returned by the server logic) must return `true`.
2. when a fixed content type is specified by the output, it must match the request's `Accept` header (if present). 
   This implements content negotiation.

When decoding from a response, the first output which decodes successfully is chosen.

The outputs might vary in status codes, headers (e.g. different content types), and body implementations. However, for 
bodies, only replayable ones can be used, and they need to have the same raw representation (e.g. all byte-array-base, 
or all file-based).

Note that exhaustiveness of the variants (that all subtypes of `T` are covered) is not checked.

For example, below is a specification for an endpoint where the error output is a sealed trait `ErrorInfo`; 
such a specification can then be refined and reused for other endpoints:

```scala
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import sttp.model.StatusCode
import io.circe.generic.auto.*

sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case class Unauthorized(realm: String) extends ErrorInfo
case class Unknown(code: Int, msg: String) extends ErrorInfo
case object NoContent extends ErrorInfo

// here we are defining an error output, but the same can be done for regular outputs
val baseEndpoint = endpoint.errorOut(
  oneOf[ErrorInfo](
    oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound].description("not found"))),
    oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[Unauthorized].description("unauthorized"))), 
    oneOfVariant(statusCode(StatusCode.NoContent).and(emptyOutputAs(NoContent))),
    oneOfDefaultVariant(jsonBody[Unknown].description("unknown"))
  )
)
```

### One-of-variant and type erasure

Type erasure may prevent a one-of-variant from working properly. The following example will fail at compile time because `Right[NotFound]` and `Right[BadRequest]` will become `Right[Any]`:

```scala
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import sttp.model.StatusCode
import io.circe.generic.auto.*

case class ServerError(what: String)

sealed trait UserError
case class BadRequest(what: String) extends UserError
case class NotFound(what: String) extends UserError

val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    oneOfVariant(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")),
    oneOfVariant(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")),
    oneOfVariant(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")),
  )
)
// error:
// Type scala.util.Right[repl.MdocSession.MdocApp.ServerError, repl.MdocSession.MdocApp.NotFound], AppliedType(TypeRef(ThisType(TypeRef(NoPrefix,module class util)),class Right),List(TypeRef(ThisType(TypeRef(ThisType(TypeRef(ThisType(TypeRef(NoPrefix,module class repl)),module class MdocSession$)),module class MdocApp$)),class ServerError), TypeRef(ThisType(TypeRef(ThisType(TypeRef(ThisType(TypeRef(NoPrefix,module class repl)),module class MdocSession$)),module class MdocApp$)),class NotFound))) is not the same as its erasure. Using a runtime-class-based check it won't be possible to verify that the input matches the desired type. Use other methods to match the input to the appropriate variant instead.
//     oneOfVariant(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")),
//                                                                                                       ^
// error:
// Type scala.util.Right[repl.MdocSession.MdocApp.ServerError, repl.MdocSession.MdocApp.BadRequest], AppliedType(TypeRef(ThisType(TypeRef(NoPrefix,module class util)),class Right),List(TypeRef(ThisType(TypeRef(ThisType(TypeRef(ThisType(TypeRef(NoPrefix,module class repl)),module class MdocSession$)),module class MdocApp$)),class ServerError), TypeRef(ThisType(TypeRef(ThisType(TypeRef(ThisType(TypeRef(NoPrefix,module class repl)),module class MdocSession$)),module class MdocApp$)),class BadRequest))) is not the same as its erasure. Using a runtime-class-based check it won't be possible to verify that the input matches the desired type. Use other methods to match the input to the appropriate variant instead.
//     oneOfVariant(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")),
//                                                                                                              ^
// error:
// Type scala.util.Left[repl.MdocSession.MdocApp.ServerError, repl.MdocSession.MdocApp.UserError], AppliedType(TypeRef(ThisType(TypeRef(NoPrefix,module class util)),class Left),List(TypeRef(ThisType(TypeRef(ThisType(TypeRef(ThisType(TypeRef(NoPrefix,module class repl)),module class MdocSession$)),module class MdocApp$)),class ServerError), TypeRef(ThisType(TypeRef(ThisType(TypeRef(ThisType(TypeRef(NoPrefix,module class repl)),module class MdocSession$)),module class MdocApp$)),trait UserError))) is not the same as its erasure. Using a runtime-class-based check it won't be possible to verify that the input matches the desired type. Use other methods to match the input to the appropriate variant instead.
//     oneOfVariant(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")),
//                                                                                                                     ^
```

The solution is therefore to handwrite a function checking that a value (of type `Any`) is of the correct type:


```scala
val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")) {
      case Right(NotFound(_)) => true
    },
    oneOfVariantValueMatcher(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")) {
      case Right(BadRequest(_)) => true
    },
    oneOfVariantValueMatcher(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized")) {
      case Left(ServerError(_)) => true
    }
  )
)
```

Of course, you could use `oneOfVariantValueMatcher` to do runtime filtering for other purpose than solving type erasure.

In the case of solving type erasure, writing by hand partial function to match value against composition of case class and sealed trait can be repetitive.
To make that more easy, we provide the `MatchType` typeclass, so you can automatically derive that partial function:

```scala
import sttp.tapir.typelevel.MatchType

val baseEndpoint = endpoint.errorOut(
  oneOf[Either[ServerError, UserError]](
    oneOfVariantFromMatchType(StatusCode.NotFound, jsonBody[Right[ServerError, NotFound]].description("not found")),
    oneOfVariantFromMatchType(StatusCode.BadRequest, jsonBody[Right[ServerError, BadRequest]].description("unauthorized")),
    oneOfVariantFromMatchType(StatusCode.InternalServerError, jsonBody[Left[ServerError, UserError]].description("unauthorized"))
  )
)
```

### One-of-variant and singleton types

One-of variants can also be created so that they are used only for specific values. This is a specialisation of the
`oneOfVariantValueMatcher` methods, which allows for a more convenient and compact description.

There are two methods which allows working with multiple or single specific values: `oneOfVariantExactMatcher` and
`oneOfVariantSingletonMatcher`.

### Error outputs

Error outputs can be extended with new variants, which is especially useful for partial server endpoints, when the
[security logic](../server/logic.md) is already provided. There are some specialised functions for this purpose.

The `.errorOutVariant` and `.errorOutVariants` functions allow appending alternative error outputs; the result is typed
as the common supertype of the existing and new outputs; hence usually this should be different from `Any`. At runtime,
a class check is performed to choose the variant to use.

The `.errorOutVariantPrepend` function allows prepending an error out variant, leaving the current error output as
a default. This is useful e.g. when providing a more specific error output, than the current one. For example:

```scala
import sttp.tapir.*

trait DomainException {
  def help: String
}
case class SecurityException(help: String) extends DomainException
case class LogicException(help: String) extends DomainException

val base: PublicEndpoint[Unit, DomainException, Unit, Any] = endpoint
  .errorOut(
    oneOf(
      oneOfVariant(statusCode(StatusCode.BadRequest).and(stringBody.mapTo[LogicException])),
      oneOfDefaultVariant(
        statusCode(StatusCode.InternalServerError).and(stringBody.map(v => new DomainException { def help: String = v })(_.help))
      )
    )
  )

val specialised: PublicEndpoint[Unit, DomainException, Unit, Any] = base
  .errorOutVariantPrepend(oneOfVariant(statusCode(StatusCode.Forbidden).and(stringBody.mapTo[SecurityException])))
```

The `.errorOutEither` method allows adding an unrelated error output, at the cost of wrapping 
the result in an additional `Either`.

## `oneOfBody` inputs/outputs

Input/output bodies which can be represented using different content types can be specified using `oneOfBody` inputs/outputs.
Each body variant should represent the same content, and hence have the same high-level (decoded) type.

To describe a body, which can be given as json, xml or plain text, create the following input/output description:

```scala
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

case class User(name: String)

implicit val userXmlCodec: Codec[String, User, CodecFormat.Xml] =
  Codec
    .id(CodecFormat.Xml(), Schema.string[String])
    .mapDecode { xml =>
      DecodeResult.fromOption("""<name>(.*?)</name>""".r.findFirstMatchIn(xml).map(_.group(1)).map(User))
    }(user => s"<name>${user.name}</name>")
    .schema(implicitly[Schema[User]])     

oneOfBody(
  jsonBody[User],
  xmlBody[User],
  stringBody.map(User(_))(_.name)
)
```

## oneOf and non-blocking streaming

[Streaming bodies](streaming.md) can't be used as normal inputs/outputs, as the streaming requirement needs to be 
propagated to the `Endpoint` type. This way, we assure at compile-time that only interpreters supporting the given 
streaming type can be used to interpret an endpoint.

However, this makes it impossible to use streaming bodies in `oneOf` (via `oneOfVariant`) and `oneOfBody`, as both
require normal input/outputs as parameters. To bypass this limitation, a `.toEndpointIO` method is available
on streaming bodies, which "lifts" them to an `EndpointIO` type, forgetting the streaming requirement. This decreases
type safety, as a run-time error might occur if an incompatible interpreter is used, however allows describing 
endpoints which require including streaming bodies in output variants.

```{note}
If the same streaming body description is used in all branches of a `oneOf`, this can be refactored into
a regular streaming body output + a varying set of output headers, expressed using `oneOf`.
```

```{warning}
Mixed streaming and non-streaming bodies defined as `oneOf` variants currently won't work with client interpreters.
```

## Next

Read on about [codecs](codecs.md).
