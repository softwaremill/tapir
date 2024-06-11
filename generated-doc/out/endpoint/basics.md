# Basics

An endpoint is represented as a value of type `Endpoint[A, I, E, O, R]`, where:

* `A` is the type of security input parameters
* `I` is the type of input parameters
* `E` is the type of error-output parameters
* `O` is the type of output parameters
* `R` are the capabilities that are required by this endpoint's inputs/outputs, such as support for websockets or a particular non-blocking streaming implementation. `Any`, if there are no such requirements.

Input/output parameters (`A`, `I`, `E` and `O`) can be:

* of type `Unit`, when there's no input/output
* a single type
* a tuple of types

Hence, an empty, initial endpoint, with no inputs and no outputs, from which all other endpoints are  derived has the type:

```scala
import sttp.tapir._

val endpoint: Endpoint[Unit, Unit, Unit, Unit, Any] = ???
```

For endpoints which have no security inputs, a type alias is provided which fixes `A` to `Unit`:

```scala
import sttp.tapir._

type PublicEndpoint[I, E, O, -R] = Endpoint[Unit, I, E, O, R]
```

A public endpoint that has two inputs of types `UUID` and `Int`, upon error returns a `String`, and on normal 
completion returns a `User`, would have the type:

 
```scala
import sttp.tapir._

val userEndpoint: PublicEndpoint[(UUID, Int), String, User, Any] = ???
```

You can think of an endpoint as a function which takes input parameters of type `A` and `I` and returns a result of type 
`Either[E, O]`.

## Infallible endpoints

Note that the empty `endpoint` description maps no values to either error and success outputs, however errors
are still represented and allowed to occur. In case of the error output, the single member of the unit type, `(): Unit`, 
maps to an empty-body `400 Bad Request`.

If you prefer to use an endpoint description where errors cannot happen use 
`infallibleEndpoint: PublicEndpoint[Unit, Nothing, Unit, Any]`. This might be useful when
interpreting endpoints [as a client](../client/sttp.md).

## Defining an endpoint

The description of an endpoint is an immutable case class, which includes a number of methods:

* the `name`, `description`, etc. methods allow modifying the endpoint information, which will then be included in the 
  endpoint documentation
* the `get`, `post` etc. methods specify the HTTP method which the endpoint should support
* the `securityIn`, `in`, `errorOut` and `out` methods allow adding a new input/output parameter
* `mapIn`, `mapInTo`, ... methods allow mapping the current input/output parameters to another value or to a case class

An important note on mapping: in tapir, all mappings are bi-directional. That's because each mapping can be used to 
generate a server or a client, as well as in many cases can be used both for input and for output.

## Next

Read on about describing [endpoint inputs/outputs](ios.md).
