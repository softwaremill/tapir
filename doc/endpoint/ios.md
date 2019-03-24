# Defining endpoint's input/output

An input is described by an instance of the `EndpointInput` trait, and an output by an instance of the `EndpointIO` 
trait, as all outputs can also be used as inputs. Each input or output can yield/accept a value. For example, 
`query[Int]("age"): EndpointInput[Int]` describes an input, which is the `age` query parameter, and which should be 
mapped (using the string-to-integer [codec](codecs.html)) as an `Int`.

The `tapir` package contains a number of convenience methods to define an input or an output for an endpoint. 
These are:

* `path[T]`, which captures a path segment as an input parameter of type `T`
* any string, which will be implicitly converted to a constant path segment. Path segments can be combined with the `/` 
  method, and don't map to any values (have type `EndpointInput[Unit]`)
* `paths`, which maps to the whole remaining path as a `Seq[String]`
* `query[T](name)` captures a query parameter with the given name
* `queryParams` captures all query parameters, represented as `MultiQueryParams`
* `header[T](name)` captures a header with the given name
* `headers` captures all headers, represented as `Seq[(String, String)]`
* `cookie[T](name)` captures a cookie from the `Cookie` header with the given name 
* `cookies` captures cookies from the `Cookie` header and represents them as `List[Cookie]` 
* `setCookie(name)` captures the value & metadata of the a `Set-Cookie` header with a matching name 
* `setCookies` captures cookies from the `Set-Cookie` header and represents them as `List[SetCookie]` 
* `body[T, M]`, `stringBody`, `plainBody[T]`, `jsonBody[T]`, `binaryBody[T]`, `formBody[T]`, `multipartBody[T]` 
  captures the body
* `streamBody[S]` captures the body as a stream: only a client/server interpreter supporting streams of type `S` can be 
  used with such an endpoint
* `extractFromRequest` extracts a value from the request. This input is only used by server interpreters, ignored
  by documentation interpreters. Client interpreters ignore the provided value. 

For outputs, you can use the `header`, `setCookies` and `body` family of methods.

## Combining inputs and outputs

Endpoint inputs/outputs can be combined in two ways. However they are combined, the values they represent always 
accumulate into tuples of values.

First, descriptions can be combined using the `.and` method. Such a combination results in an input/output represented 
as a tuple of the given types, can be stored as a value and re-used in multiple endpoints. As all other values in tapir, 
endpoint input/output descriptions are immutable. For example, an input specifying two query parameters, `start` 
(mandatory) and `limit` (optional) can be written down as:

```scala
val paging: EndpointInput[(UUID, Option[Int])] = 
  query[UUID]("start").and(query[Option[Int]]("limit"))

// we can now use the value in multiple endpoints, e.g.:
val listUsersEndpoint: Endpoint[(UUID, Option[Int]), Unit, List[User], Nothing] = 
  endpoint.in("user" / "list").in(paging).out(jsonBody[List[User]])
```

Second, inputs can be combined by calling the `in`, `out` and `errorOut` methods on `Endpoint` multiple times. Each time 
such a method is invoked, it extends the list of inputs/outputs. This can be useful to separate different groups of 
parameters, but also to define template-endpoints, which can then be further specialized. For example, we can define a 
base endpoint for our API, where all paths always start with `/api/v1.0`, and errors are always returned as a json:

```scala
val baseEndpoint: Endpoint[Unit, ErrorInfo, Unit, Nothing] =  
  endpoint.in("api" / "v1.0").errorOut(jsonBody[ErrorInfo])
```

Thanks to the fact that inputs/outputs accumulate, we can use the base endpoint to define more inputs, for example:

```scala
val statusEndpoint: Endpoint[Unit, ErrorInfo, Status, Nothing] = 
  baseEndpoint.in("status").out(jsonBody[Status])
```

The above endpoint will correspond to the `api/v1.0/status` path.

## Mapping over input values

Inputs/outputs can also be mapped over. As noted before, all mappings are bi-directional, so that they can be used both
when interpreting an endpoint as a server, and as a client.

There's a couple of ways to map over an input/output. First, there's the `map[II](f: I => II)(g: II => I)` method, 
which  accepts functions which provide the mapping in both directions. For example:

```scala
case class Paging(from: UUID, limit: Option[Int])

val paging: EndpointInput[Paging] = 
  query[UUID]("start").and(query[Option[Int]]("limit"))
    .map((from, limit) => Paging(from, limit))(paging => (paging.from, paging.limit))
```

Creating a mapping between a tuple and a case class is a common operation, hence there's also a 
`mapTo(CaseClassCompanion)` method, which automatically provides the mapping functions:

```scala
case class Paging(from: UUID, limit: Option[Int])

val paging: EndpointInput[Paging] = 
  query[UUID]("start").and(query[Option[Int]]("limit"))
    .mapTo(Paging)
```

Mapping methods can also be called on an endpoint (which is useful if inputs/outputs are accumulated, for example).
The `Endpoint.mapIn`, `Endpoint.mapInTo` etc. have the same signatures are the ones above.

## Path matching

By default (as with all other types of inputs), if no path input/path segments are defined, any path will match.

If any path input/path segment is defined, the path must match *exactly* - any remaining path segments will cause the
endpoint not to match the request. For example, `endpoint.path("api")` will match `/api`, `/api/`, but won't match
`/`, `/api/users`.

To match only the root path, use an empty string: `endpoint.path("")` will match `http://server.com/` and 
`http://server.com`.

## Status codes

It is possible to specify how the value of an output (typically the body) maps to the status code. This is used
when interpreting the endpoint as a server and when generating documentation. 

For example, below is a specification for an endpoint where the error output is fixed to be of type `ErrorInfo`; 
such a specification can then be refined and reused for other endpoints:

```scala
case class ErrorInfo(errorType: ErrorType, msg: String)

val baseEndpoint = endpoint.errorOut(
  statusFrom(
    jsonBody[ErrorType],
    StatusCodes.BadRequest,
    whenValue[ErrorType](_.errorType == ErrorType.NotFound, StatusCodes.NotFound),
    whenValue[ErrorType](_.errorType == ErrorType.Exception, StatusCodes.InternalServerError)
  )
)
```

The `statusFrom` method takes as parameters: the wrapped output, default status code, and any number of specific
status codes mappings based on the value (`whenValue`) or class (`whenClass`) of the output value.

## Next

Read on about [codecs](codecs.html).
