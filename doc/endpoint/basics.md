# Basics

An endpoint is represented as a value of type `Endpoint[I, E, O, S]`, where:

* `I` is the type of the input parameters
* `E` is the type of the error-output parameters
* `O` is the type of the output parameters
* `S` is the type of streams that are used by the endpoint's inputs/outputs

Input/output parameters (`I`, `E` and `O`) can be:

* of type `Unit`, when there's no input/ouput of the given type
* a single type
* a tuple of types

Hence, an empty, initial endpoint (`tapir.endpoint`), with no inputs and no outputs, from which all other endpoints are 
derived has the type:

```scala
val endpoint: Endpoint[Unit, Unit, Unit, Nothing] = ...
```

An endpoint which accepts two parameters of types `UUID` and `Int`, upon error returns a `String`, and on normal 
completion returns a `User`, would have the type:
 
```scala
Endpoint[(UUID, Int), String, User, Nothing]
```

You can think of an endpoint as a function, which takes input parameters of type `I` and returns a result of type 
`Either[E, O]`, where inputs or outputs can contain streaming bodies of type `S`.

### Infallible endpoints

Note that the empty `endpoint` description maps no values to either error and success outputs, however errors
are still represented and allowed to occur. If you would prefer to use an endpoint description, where
errors can not happen, use `infallibleEndpoint: Endpoint[Unit, Nothing, Unit, Nothing]`. This might be useful when
interpreting endpoints [as a client](../sttp.html).

## Defining an endpoint

The description of an endpoint is an immutable case class, which includes a number of methods:

* the `name`, `description`, etc. methods allow modifying the endpoint information, which will then be included in the 
  endpoint documentation
* the `get`, `post` etc. methods specify the HTTP method which the endpoint should support
* the `in`, `errorOut` and `out` methods allow adding a new input/output parameter
* `mapIn`, `mapInTo`, ... methods allow mapping the current input/output parameters to another value or to a case class

An important note on mapping: in tapir, all mappings are bi-directional. That's because each mapping can be used to 
generate a server or a client, as well as in many cases can be used both for input and for output.

## Next

Read on about describing [endpoint inputs/outputs](ios.html).
