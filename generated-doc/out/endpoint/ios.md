# Inputs/outputs

An input is described by an instance of the `EndpointInput` trait, and an output by an instance of the `EndpointOutput`
trait. Some inputs can be used both as inputs and outputs; then, they additionally implement the `EndpointIO` trait.

Each input or output can yield/accept a value (but doesn't have to).

For example, `query[Int]("age"): EndpointInput[Int]` describes an input, which is the `age` parameter from the URI's
query, and which should be coded (using the string-to-integer [codec](codecs.md)) as an `Int`.

The `tapir` package contains a number of convenience methods to define an input or an output for an endpoint. 
For inputs, these are:

* `path[T]`, which captures a path segment as an input parameter of type `T`
* any string, which will be implicitly converted to a fixed path segment. Constant path segments can be combined with 
  the `/` method, and don't map to any values (they have type `EndpointInput[Unit]`, but they still modify the 
  endpoint's behavior)
* `paths`, which maps to the whole remaining path as a `List[String]`
* `query[T](name)` captures a query parameter with the given name
* `queryParams` captures all query parameters, represented as `QueryParams`
* `cookie[T](name)` captures a cookie from the `Cookie` header with the given name
* `extractFromRequest` extracts a value from the request. This input is only used by server interpreters, ignored
  by documentation interpreters. Client interpreters ignore the provided value. It can also be used to access the
  original request through the `underlying: Any` field.

For both inputs/outputs:

* `header[T](name)` captures a header with the given name
* `header[T](name, value)` maps to a fixed header with the given name and value
* `headers` captures all headers, represented as `List[Header]`
* `cookies` captures cookies from the `Cookie` header and represents them as `List[Cookie]` 
* `setCookie(name)` captures the value & metadata of the a `Set-Cookie` header with a matching name 
* `setCookies` captures cookies from the `Set-Cookie` header and represents them as `List[SetCookie]` 
* `stringBody`, `plainBody[T]`, `jsonBody[T]`, `rawBinaryBody[R]`, `binaryBody[R, T]`, `formBody[T]`, `multipartBody[T]`
  captures the body
* `streamBody[S]` captures the body as a stream: only a client/server interpreter supporting streams of type `S` can be 
  used with such an endpoint
* `oneOfBody` captures multiple variants of bodies representing the same content, but using different content types

For outputs:

* `statusCode` maps to the status code of the response
* `statusCode(code)` maps to a fixed status code of the response

## Combining inputs and outputs

Endpoint inputs/outputs can be combined in two ways. However they are combined, the values they represent always 
accumulate into tuples of values.

First, inputs/outputs can be combined using the `.and` method. Such a combination results in an input/output which maps
to a tuple of the given types. This combination can be assigned to a value and re-used in multiple endpoints. As all 
other values in tapir, endpoint input/output descriptions are immutable. For example, an input specifying two query 
parameters, `start` (mandatory) and `limit` (optional) can be written down as:

```scala
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*
import java.util.UUID

case class User(name: String)

val paging: EndpointInput[(UUID, Option[Int])] = 
  query[UUID]("start").and(query[Option[Int]]("limit"))

// we can now use the value in multiple endpoints, e.g.:
val listUsersEndpoint: PublicEndpoint[(UUID, Option[Int]), Unit, List[User], Any] = 
  endpoint.in("user" / "list").in(paging).out(jsonBody[List[User]])
```

Second, inputs can be combined by calling the `in`, `out` and `errorOut` methods on `Endpoint` multiple times. Each time 
such a method is invoked, it extends the list of inputs/outputs. This can be useful to separate different groups of 
parameters, but also to define template-endpoints, which can then be further specialized. For example, we can define a 
base endpoint for our API, where all paths always start with `/api/v1.0`, and errors are always returned as a json:

```scala
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

case class ErrorInfo(message: String)

val baseEndpoint: PublicEndpoint[Unit, ErrorInfo, Unit, Any] =  
  endpoint.in("api" / "v1.0").errorOut(jsonBody[ErrorInfo])
```

Thanks to the fact that inputs/outputs accumulate, we can use the base endpoint to define more inputs, for example:

```scala
case class Status(uptime: Long)

val statusEndpoint: PublicEndpoint[Unit, ErrorInfo, Status, Any] = 
  baseEndpoint.in("status").out(jsonBody[Status])
```

The above endpoint will correspond to the `/api/v1.0/status` path.

## Mapping over input/output values

Inputs/outputs can also be mapped over. As noted before, all mappings are bi-directional, so that they can be used both
when interpreting an endpoint as a server, and as a client, as well as both in input and output contexts.

There's a couple of ways to map over an input/output. First, there's the `map[II](f: I => II)(g: II => I)` method, 
which accepts functions which provide the mapping in both directions. For example:

```scala
import sttp.tapir.*
import java.util.UUID

case class Paging(from: UUID, limit: Option[Int])

val paging: EndpointInput[Paging] = 
  query[UUID]("start").and(query[Option[Int]]("limit"))
    .map(input => Paging(input._1, input._2))(paging => (paging.from, paging.limit))
```

Next, you can use `mapDecode[II](f: I => DecodeResult[II])(g: II => I)`, to handle cases where decoding (mapping a 
low-level value to a higher-value one) can fail. There's a couple of failure reasons, captured by the alternatives
of the `DecodeResult` trait.

Mappings can also be done given a `Mapping[I, II]` instance. More on that in the section on [codecs](codecs.md).

Creating a mapping between a tuple and a case class is a common operation, hence there's also a 
`mapTo[CaseClass]` method, which automatically provides the functions to construct/deconstruct the case class:

```scala
val paging: EndpointInput[Paging] = 
  query[UUID]("start").and(query[Option[Int]]("limit"))
    .mapTo[Paging]
```

Mapping methods can also be called on an endpoint (which is useful if inputs/outputs are accumulated, for example).
The `Endpoint.mapIn`, `Endpoint.mapInTo` etc. have the same signatures are the ones above.

## Describing input/output values using annotations

Inputs and outputs can also be built for case classes using annotations. For example, for the case class `User`
```scala
import sttp.tapir.EndpointIO.annotations.*

case class User(
  @query
  name: String,
  @cookie
  sessionId: Long
)
```

endpoint input can be generated using macro `EndpointInput.derived[User]` which is equivalent to

```scala
import sttp.tapir.*

val userInput: EndpointInput[User] =
  query[String]("user").and(cookie[Long]("sessionId")).mapTo[User]
```

Similarly, endpoint outputs can be derived using `EndpointOutput.derived[...]`.

Following annotations are available in package `sttp.tapir.annotations` for describing both input and output values:

* `@header` captures a header with the same name as name of annotated field in a case class. This annotation
can also be used with optional parameter `@header("headerName")` in order to capture a header with name `"headerName"`
if a name of header is different from name of annotated field in a case class
* `@headers` captures all headers. Can only be applied to fields represented as `List[Header]`
* `@cookies` captures all cookies. Can only be applied to fields represented as `List[Cookie]`
* `@jsonbody` captures JSON body of request or response. Can only be applied to field if there is implicit JSON `Codec`
instance from `String` to target type
* `@xmlbody` captures XML body of request or response. Also requires implicit XML `Codec` instance from `String` to
target type

Following annotations are only available for describing input values:

* `@query` captures a query parameter with the same name as name of annotated field in a case class. The same as
annotation `@header` it has optional parameter to specify alternative name for query parameter
* `@params` captures all query parameters. Can only be applied to fields represented as `QueryParams`
* `@cookie` captures a cookie with the same name as name of annotated field in a case class. The same as annotation
`@header` it has optional parameter to specify alternative name for cookie
* `@apikey` wraps any other input and designates it as an API key. Can only be used with another annotations
* `@basic` extracts data from the `Authorization` header. Can only be applied for field represented as `UsernamePassword`
* `@bearer` extracts data from the `Authorization` header removing the `Bearer` prefix.  
* `@path` captures a path segment. Can only be applied to field of a case class if this case class is annotated
by annotation `@endpointInput`. For example,
  
```scala
import sttp.tapir.EndpointIO.annotations.*

@endpointInput("books/{year}/{genre}")
case class Book(
  @path
  genre: String,
  @path
  year: Int,
  @query
  name: String
)
```

Annotation `@endpointInput` specifies endpoint path. In order to capture a segment of the path, it must be surrounded
in curly braces.

Following annotations are only available for describing output values:

* `@setCookie` sends value in header `Set-Cookie`. The same as annotation `@header` it has optional parameter to specify
alternative name for cookie. Can only be applied for field represented as `CookieValueWithMeta`
* `@setCookies` sends several `Set-Cookie` headers. Can only be applied for field represented as `List[Cookie]`
* `@statusCode` sets status code for response. Can only be applied for field represented as `StatusCode`

## Status codes 

### Arbitrary status codes

To provide a (varying) status code of a server response, use the `statusCode` output, which maps to a value of type
`sttp.model.StatusCode`. In a server setting, the specific status code will then have to be provided dynamically by the 
server logic. The companion object contains known status codes as constants. This type of output is used only 
when interpreting the endpoint as a server. If your endpoint returns varying status codes which you would like to have 
listed in documentation use `statusCode.description(code1, "code1 description").description(code2, "code2 description")` 
output.

### Fixed status code

A fixed status code can be specified using the `statusCode(code)` output.

### In server interpreters

Unless specified otherwise, successful responses are returned with the `200 OK` status code, and errors with
`400 Bad Request`. For exception and decode failure handling, see [error handling](../server/errors.md).

### Different outputs with different status codes

If you'd like to return different content together with a varying status code, use a [oneOf](oneof.md) output.
Each output variant can be paired with a fixed status code output (`statusCode(code)`), or a varying one, which will
be determined dynamically by the server logic.

## Selected inputs/outputs for non-standard types

* some header values can be decoded into a more structured representation, e.g. `header[MediaType]`, `header[ETag]`, 
  `header[Range]`, `header[List[CacheDirective]]`, `header[List[Cookie]]`, `header[CookieWithMeta]`
* the low-level body value can be tupled with the decoded high-level representation. This is useful e.g. if the
  hash of the body is required for security. A dedicated `jsonBodyWithRaw` description is available, but this
  can be used for any body e.g. `plainBody[(String, Int)]`
* an input can be decoded into either one of two high-level values, e.g. `query[Either[String, Int]]("param")`. By 
  default, such decoding is right-biased, so the right-hand codec is attempted fails, and only if it fails, the 
  left-hand side codec is used

## Next

Read on about [one-of mappings](oneof.md).
