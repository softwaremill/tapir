# 3. Shape of IOs

Date: 2019-11-09

## Context

The shape of tapir's inputs and outputs is fixed and in some ways constrained. Below you'll find some motivation
behind this design, as well as alternatives.

## Decision

The *input* of an endpoint is always a product of values: that is, each new input extends the list of values that the
endpoint's input maps to. A new input can contribute 0 values (in case of fixed paths), 1 value (query parameter, 
path capture, ...), or many values (a composite input).

What is *not* possible, is describing a *coproduct*: specifying, that the input is either a value of one type, or a 
value of another type. Coproducts are harder than products as they need a *discriminator*: some kind of value, basing 
on which it can be decided, which of the input alternatives to choose.

It would be possible to extend tapir and allow coproducts, at the expense of complicating the API. However, use-cases 
which require such mappings are rare (or so it seems), so this isn't currently implemented. And there's always a
"back door": an alternative of values can be described as a "flattened" product of optional values, with additional 
input validation.

*Outputs* are a bit more complicated. First of all, success and error outputs are separate. This defines a top-level
coproduct, where the output is either mapped to the branch error, or the success branch. The discriminator in this case
is the status code.

However, both error and success outputs can contain coproducts as well, using the `oneOf` output. The `oneOf` output
specifies a number of alternative outputs, again discriminated using fixed status code values (which is important to be
able to generate documentation). The types, to which the branches should map, have to form an inheritance hierarchy, 
e.g.:

```scala
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._

sealed trait ErrorInfo
case class NotFound(what: String) extends ErrorInfo
case class Unauthorized(realm: String) extends ErrorInfo
case class Unknown(code: Int, msg: String) extends ErrorInfo

val baseEndpoint = endpoint.errorOut(
  oneOf(
    oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
    oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized]),
    oneOfDefaultVariant(jsonBody[Unknown].description("unknown"))
  )
)
```

Again, this could be generalised to allow other discriminators (e.g. on fixed header values), however there are no
compelling use-cases which would justify this.

It would also be possible to generalise the error/success outputs into a single output type. Users could then use the
`oneOf` output to differentiate between errors and successes, for example:

```scala
val e1: Endpoint[Unit, Either[String, Book], Nothing] = endpoint
  .out(either(
    statusCode(200) -> jsonBody[Book],
    statusCode(400) -> stringBody
  ))
  
val e2: Endpoint[Unit, Either[ErrorInfo, Book], Nothing] = endpoint
  .out(either(
    statusCode(200) -> jsonBody[Book],
    otherwise -> oneOf[ErrorInfo]( 
      statusCode(404) -> jsonBody[NotFound], 
      statusCode(403) -> jsonBody[Unauthorized],
      statusCode(400) -> jsonBody[Unknown]
    )))
```

However, error outputs are almost always *different* from success outputs, so it's worth complicating the API to 
support this distinction as a first-class construct. Moreover, quite often endpoints share the error output description,
while the success output vary from endpoint to endpoint.
