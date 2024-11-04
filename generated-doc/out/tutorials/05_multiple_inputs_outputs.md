# 5. Multiple inputs & outputs

```{note}
The tutorial is also available [as a video](https://www.youtube.com/watch?v=rJAo9yZfr9k).
```

In the tutorials so far we've seen how to use endpoints which have a single input and a single output, optionally with
an additional single error output. However, most commonly you'll have multiple inputs and outputs. This can
include multiple path, query parameters and headers, accompanied by a body as inputs, along with multiple output
headers, accompanied by a status code and body output. 

That's why in this tutorial we'll examine how to describe endpoints with multiple inputs/outputs and map them to
high-level types.

## Describing the endpoint

Adding multiple inputs/outputs is simply a matter of calling `.in` or `.out` on an endpoint description multiple 
times. 

To demonstrate how this works, let's describe an `/operation/{opName}?value1=...&value2=...` endpoint, where
`opName` can be either `add` or `sub`, and `value1` and `value2` should be numbers. The result should be returned in the
body, but additionally the hash of the result should be included in the `X-Result-Hash` custom header.

Below is the endpoint description; we'll be editing the `multiple.scala` file:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8

import sttp.tapir.*

@main def tapirMultiple(): Unit =
  val opEndpoint = endpoint.get
    .in("operation" / path[String]("opName"))
    .in(query[Int]("value1"))
    .in(query[Int]("value2"))
    .out(stringBody)
    .out(header[String]("X-Result-Hash"))
    .errorOut(stringBody)
```

In our endpoint, we have:

* 5 inputs: 1 constant method input (`.get`), 1 constant path input (`"operation"`), 1 path-segment-capturing input 
  (`opName`), 2 query parameter inputs
* 2 outputs: a body and a header
* 1 error output: a string body, which we'll use in case an invalid operation name is provided

## Server logic

When we provide the server logic for our endpoint, the values of the inputs are extracted from the HTTP request, and
values from the output are mapped to the HTTP response. When there are multiple inputs, their values are by default
extracted as a tuple. Conversely, the server logic must return a tuple, if there are multiple outputs.

Only values of inputs which are non-constant contribute to the input tuple. That is, in our case, a 3-tuple will be 
extracted from the HTTP request: `(String, Int, Int)`, corresponding to the path segment and query parameters. The 
constant method & path inputs are used when matching an endpoint with an incoming request, but do not contribute to the 
extracted values.

Here's the code with the server logic provided, transforming a `(String, Int, Int)` tuple to a `(String, String)` tuple.
The output tuple is then mapped to the response body & header:

{emphasize-lines="5, 8-9, 18-29"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def tapirMultiple(): Unit =
  def hash(result: Int): (String, String) =
    (result.toString, scala.util.hashing.MurmurHash3.stringHash(result.toString).toString)

  val opEndpoint = endpoint.get
    .in("operation" / path[String]("opName"))
    .in(query[Int]("value1"))
    .in(query[Int]("value2"))
    .out(stringBody)
    .out(header[String]("X-Result-Hash"))
    .errorOut(stringBody)
    // (String, Int, Int) => Either[String, (String, String)]
    .handle { (op, v1, v2) =>
      op match
        case "add" => Right(hash(v1 + v2))
        case "sub" => Right(hash(v1 - v2))
        case _     => Left("Unknown operation. Available operations: add, sub")
    }

  NettySyncServer()
    .port(8080)
    .addEndpoint(opEndpoint)
    .startAndWait()
```

The user's input might be incorrect - that is, specify an unsupported operation name - in that case we return an error. 
That's why we need to wrap the result of the server logic either in a `Left` or `Right`, to use the error or success
output.

```{note}
In subsequent tutorials, we'll see how to better handle input parameters such as `opName` using enumerations.
```

We can now run some tests:

```bash
# first console
% scala-cli multiple.scala

# second console
% curl -v "http://localhost:8080/operation/add?value1=10&value2=14"
< X-Result-Hash: 1385572155
24

% curl -v "http://localhost:8080/operation/sub?value1=10&value2=8" 
< X-Result-Hash: 382493853
2              
```

## Mapping to case classes

We could stop there, but the server logic's signature `(String, Int, Int) => Either[String, (String, String)]` isn't
the most readable or developer-friendly. It would be much better (and less error-prone!) to use some custom data types,
and avoid using raw `String`s and `Int`s everywhere.

Let's define some case classes, which we'll use to capture the inputs and outputs, and give the parameters meaningful
names:

```scala
case class Input(opName: String, value1: Int, value2: Int)
case class Output(result: String, hash: String)
```

Our goal now is to change the endpoint's description so that the server logic has the shape 
`Input => Either[String, Output]`.

This can be done by mapping the inputs and outputs, that are defined on an endpoint, to our high-level types. 

For inputs, we can use the `.mapIn` method. We need to provide two-way conversions, between `(String, Int, Int)` and 
`Input`. The tuple => `Input` conversion is used for incoming requests: first the values are extracted, and then the
mapping function is applied, yielding an `Input` instance, which is provided to the server logic. 

However, you also need to provide the `Input` => tuple conversion, in case the endpoint is interpreted as a client. We
haven't covered this yet in the tutorials, but in the client-interpreter case such conversion is needed, when a request
is sent.

The mapping functions are simple, but quite boring to write:

{emphasize-lines="8, 17-18, 23-27"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def tapirMultiple(): Unit =
  case class Input(opName: String, value1: Int, value2: Int)

  def hash(result: Int): (String, String) =
    (result.toString, scala.util.hashing.MurmurHash3.stringHash(result.toString).toString)

  val opEndpoint = endpoint.get
    .in("operation" / path[String]("opName"))
    .in(query[Int]("value1"))
    .in(query[Int]("value2"))
    .mapIn((opName, value1, value2) => Input(opName, value1, value2))(input => 
      (input.opName, input.value1, input.value2))
    .out(stringBody)
    .out(header[String]("X-Result-Hash"))
    .errorOut(stringBody)
    // Input => Either[String, (String, String)]
    .handle { input =>
      input.opName match
        case "add" => Right(hash(input.value1 + input.value2))
        case "sub" => Right(hash(input.value1 - input.value2))
        case _     => Left("Unknown operation. Available operations: add, sub")
    }

  NettySyncServer()
    .port(8080)
    .addEndpoint(opEndpoint)
    .startAndWait()
```

The `.mapIn` method covers all inputs defined so far, hence we're calling it only after all inputs are defined. If we 
add more inputs later, the server logic will once again be parametrised by a tuple consisting of `Input` and the new 
inputs.

## Better mapping to case classes

There is a better way of mapping multiple inputs and outputs to cases classes - Tapir can generate the "boring" mapping
code for you. This can be done using `.mapInTo[]` and `.mapOutTo[]`. Both of these are macros, which take the target
type as a parameter. The mapping code is generated at compile-time, verifying also at compile-time that the types of the 
inputs/outputs and the types specified as the case class parameters line up.

Here's the modified code using `.mapInTo`, which additionally maps outputs to the `Output` class:

{emphasize-lines="9, 11-13, 19, 22"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def tapirMultiple(): Unit =
  case class Input(opName: String, value1: Int, value2: Int)
  case class Output(result: String, hash: String)

  def hash(result: Int): Output =
    Output(result.toString, 
      scala.util.hashing.MurmurHash3.stringHash(result.toString).toString)

  val opEndpoint = endpoint.get
    .in("operation" / path[String]("opName"))
    .in(query[Int]("value1"))
    .in(query[Int]("value2"))
    .mapInTo[Input]
    .out(stringBody)
    .out(header[String]("X-Result-Hash"))
    .mapOutTo[Output]
    .errorOut(stringBody)
    // Input => Either[String, Output]
    .handle { input =>
      input.opName match
        case "add" => Right(hash(input.value1 + input.value2))
        case "sub" => Right(hash(input.value1 - input.value2))
        case _     => Left("Unknown operation. Available operations: add, sub")
    }

  NettySyncServer()
    .port(8080)
    .addEndpoint(opEndpoint)
    .startAndWait()
```

Now our server logic has the shape `Input => Either[String, Output]`. Much better! 

## Further reading

There's much more when it comes to supporting custom types in Tapir - a crucial feature for a type-safety-oriented
library. We've seen how to use custom types using `jsonBody`, and now when combining multiple inputs and outputs.
We'll see other ways in which custom types are supported in subsequent tutorials. As always, for the impatient, here's
a couple of reference documentation links:

* [](../endpoint/customtypes.md)
* [](../endpoint/integrations.md)
