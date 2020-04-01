# Inputs/outputs

An input is described by an instance of the `EndpointInput` trait, and an output by an instance of the `EndpointOutput`
trait. Some inputs can be used both as inputs and outputs; then, they additionally implement the `EndpointIO` trait.

Each input or output can yield/accept a value (but doesn't have to).

For example, `query[Int]("age"): EndpointInput[Int]` describes an input, which is the `age` parameter from the URI's
query, and which should be coded (using the string-to-integer [codec](codecs.html)) as an `Int`.

The `tapir` package contains a number of convenience methods to define an input or an output for an endpoint. 
For inputs, these are:

* `path[T]`, which captures a path segment as an input parameter of type `T`
* any string, which will be implicitly converted to a fixed path segment. Path segments can be combined with the `/` 
  method, and don't map to any values (have type `EndpointInput[Unit]`)
* `paths`, which maps to the whole remaining path as a `Seq[String]`
* `query[T](name)` captures a query parameter with the given name
* `queryParams` captures all query parameters, represented as `MultiQueryParams`
* `cookie[T](name)` captures a cookie from the `Cookie` header with the given name
* `extractFromRequest` extracts a value from the request. This input is only used by server interpreters, ignored
  by documentation interpreters. Client interpreters ignore the provided value.

For both inputs/outputs:

* `header[T](name)` captures a header with the given name
* `headers` captures all headers, represented as `Seq[(String, String)]`
* `cookies` captures cookies from the `Cookie` header and represents them as `List[Cookie]` 
* `setCookie(name)` captures the value & metadata of the a `Set-Cookie` header with a matching name 
* `setCookies` captures cookies from the `Set-Cookie` header and represents them as `List[SetCookie]` 
* `body[T, M]`, `stringBody`, `plainBody[T]`, `jsonBody[T]`, `binaryBody[T]`, `formBody[T]`, `multipartBody[T]` 
  captures the body
* `streamBody[S]` captures the body as a stream: only a client/server interpreter supporting streams of type `S` can be 
  used with such an endpoint

For outputs:

* `statusCode` maps to the status code of the response
* `statusCode(code)` maps to a fixed status code of the response

## Combining inputs and outputs

Endpoint inputs/outputs can be combined in two ways. However they are combined, the values they represent always 
accumulate into tuples of values.

First, descriptions can be combined using the `.and` method. Such a combination results in an input/output, which maps
to a tuple of the given types, and can be stored as a value and re-used in multiple endpoints. As all other values in
tapir, endpoint input/output descriptions are immutable. For example, an input specifying two query parameters, `start`
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
when interpreting an endpoint as a server, and as a client, as well as both in input and output contexts.

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

> Note that this kind of mapping is only meant for isomorphic transformations and grouping inputs/outputs into custom
> types. To support custom types, where one of the transformations might fail, see [codecs](codecs.md) and 
> [validation](validation.md).

## Path matching

By default (as with all other types of inputs), if no path input/path segments are defined, any path will match.

If any path input/path segment is defined, the path must match *exactly* - any remaining path segments will cause the
endpoint not to match the request. For example, `endpoint.in("api")` will match `/api`, `/api/`, but won't match
`/`, `/api/users`.

To match only the root path, use an empty string: `endpoint.in("")` will match `http://server.com/` and
`http://server.com`.

To match a path prefix, first define inputs which match the path prefix, and then capture any remaining part using
`paths`, e.g.: `endpoint.in("api" / "download").in(paths)"`.

## Streaming support

Both input and output bodies can be mapped to a stream, by using `streamBody[S]`. The type `S` must match the type of
streams that are supported by the interpreter: refer to the documentation of server/client interpreters for the
precise type.

Adding a stream body input/output influences both the type of the input/output, as well as the 4th type parameter
of `Endpoint`, which specifies the requirements regarding supported stream types for interpreters.

When using a stream body, the schema (for documentation) and format (media type) of the body must be provided by hand, 
as they cannot be inferred from the raw stream type. For example, to specify that the output is an akka-stream, which
is a (presumably large) serialised list of json objects mapping to the `Person` class:  

```scala
endpoint.out(streamBody[Source[ByteString, Any]](schemaFor[List[Person]], CodecFormat.Json()))
```

See also the [runnable streaming example](../examples.html). 

## Next

Read on about [status codes](statuscodes.html).
