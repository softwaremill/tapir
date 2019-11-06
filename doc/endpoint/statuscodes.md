# Status codes

To provide a (varying) status code of a server response, use the `statusCode` output, which maps to a value of type
`type tapir.model.StatusCode` (which is an alias for `Int`). The `tapir.model.StatusCodes` object contains known status 
codes as constants. This type of output is used only when interpreting the endpoint as a server. If your endpoint returns different status codes
which you would like to have listed in documentation use `oneOfStatusCodes(code1, code2, ..., coden)` output.

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
    statusMapping(StatusCodes.NotFound, jsonBody[NotFound].description("not found")),
    statusMapping(StatusCodes.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
    statusMapping(StatusCodes.NoContent, emptyOutput.map(_ => NoContent)(_ => ())),
    statusDefaultMapping(jsonBody[Unknown].description("unknown"))
  )
)
```

Each mapping, defined using the `statusMapping` method is a case class, containing the output description as well as
the status code. Moreover, default mappings can be defined using `statusDefaultMapping`:

* for servers, the default status code for error outputs is `400`, and for normal outputs `200` (unless a `statusCode` 
  is used in the nested output)
* for clients, a default mapping is a catch-all. 

## Server interpreters

Unless specified otherwise, successful responses are returned with the `200 OK` status code, and errors with 
`400 Bad Request`. For exception and decode failure handling, see [error handling](../server/errors.html).

## Next

Read on about [codecs](codecs.html).
