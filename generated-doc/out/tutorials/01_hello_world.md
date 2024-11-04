# 1. Hello, world!

To start our adventure with tapir, we'll define and expose a single endpoint using an HTTP server.

```{note}
The tutorial is also available [as a video](https://www.youtube.com/watch?v=WV1bZaGrdQQ).
```

## Prerequisites

We'll use [scala-cli](https://scala-cli.virtuslab.org) to run the code, so you'll need to install it beforehand.
You can use any text editor to write the code, but we recommend using either [Metals](https://scalameta.org/metals/) if
you're familiar with VSCode, or [IntelliJ IDEA](https://www.jetbrains.com/idea/) with the Scala plugin.

You'll also need Java 21 or higher, as we'll be using virtual threads, which allow us to use a synchronous programming
model, without sacrificing performance. If you don't have Java 21 installed, we recommend using
[sdkman](https://sdkman.io/) to manage multiple Java versions locally.

Going forward, we'll edit a `hello.scala` file. Let's start by adding the tapir dependency. First, you'll need the
`tapir-core` module to describe the endpoint. Secondly, you'll need an HTTP server implementation. Tapir integrates with
multiple servers, but we'll choose the simplest (and also one of the fastest!), which is based on Netty,
available through the `tapir-netty-server-sync` module:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
```

## Endpoint description

Once we have that, we can start describing our endpoint. This is done by taking an empty endpoint, available through
the `sttp.tapir.endpoint` value, and gradually adding more details, such as the method, path, and other input/output
parameters.

Endpoint inputs are the values that are mapped to HTTP requests; endpoint outputs are the values that are mapped to
HTTP responses. Inputs can be added to an existing endpoint description using the `Endpoint.in(...)` method, given
the input description as a parameter. An updated endpoint description is returned.

Input/output descriptions are created by methods available in the `sttp.tapir` package, hence it's often easiest to
import it entirely, using `import sttp.tapir.*`.

Let's start by defining the method and path of our endpoint:

{emphasize-lines="4-11"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint
    .get
    .in("hello" / "world")

  println(helloWorldEndpoint.show)
```

You can now run the example from the command line. We are outputting a human-friendly description of the
endpoint's structure, so you should see the following:

```bash
% scala-cli hello.scala
Compiling project (Scala 3.4.2, JVM (21))
Compiled project (Scala 3.4.2, JVM (21))
GET /hello /world -> -/-
```

So far, we've added three inputs to the endpoint: a constant method (`GET`), with `.get`; and two constant path inputs
combined using `/`. Next, we'll add a query parameter input, but this time, it will extract the provided value instead
of requiring it to be a fixed value (a constant):

{emphasize-lines="10"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint
    .get
    .in("hello" / "world")
    .in(query[String]("name"))

  println(helloWorldEndpoint.show)
```

After running, the output should now be `GET /hello /world ?name -> -/-`.

The `query[String]("name")` method creates a data structure describing a query parameter input. The description
specifies that the value should be deserialized to a `String` - we'll learn how to deserialize to other data types in
subsequent tutorials. Next, using `.in` we add this description to the data structure describing the endpoint as a
whole.

Finally, let's add an output to the endpoint. We'll return the response as a string body:

{emphasize-lines="11"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint
    .get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)

  println(helloWorldEndpoint.show)
```

## Server-side logic

Let's add the logic to run once the endpoint is invoked. This can be done using
the `.handleSuccess`  method on the endpoint. We're using the "success" variant, since in this simple endpoint
we don't differentiate between success and failure cases (200 and 4xx responses).

The server logic needs to take the `String`, extracted from the query parameter, and return another `String`, which
will be sent as a response:

{emphasize-lines="12"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint
    .get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  println(helloWorldEndpoint.show)
```

Nothing changes in the output provided by `.show`, however the `helloWorldEndpoint` is now an instance of the
`ServerEndpoint` class, which combines an endpoint description with a matching server logic. It's checked at
compile-time that the shape of the server's logic function matches the types of inputs & outputs that we've defined in
the endpoint!

## Exposing the server

We can now expose the server to the outside world. First, we'll need to import the server implementation. Then,
using the `NettySyncServer()` builder class, we can add endpoints, which the server should expose. In our
example, we'll bind to `localhost` (which is the default), and to the port 8080:

{emphasize-lines="5, 15-18"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def helloWorldTapir(): Unit =
  val helloWorldEndpoint = endpoint
    .get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  NettySyncServer()
    .port(8080)
    .addEndpoint(helloWorldEndpoint)
    .startAndWait()
```

The `startAndWait()` method blocks indefinitely. Once the above code compiles and runs successfully, we can test our
endpoint:

```bash
# first console
% scala-cli hello.scala
Compiling project (Scala 3.4.2, JVM (21))
Compiled project (Scala 3.4.2, JVM (21))

# another console
% curl "http://localhost:8080/hello/world?name=Alice"
Hello, Alice!
```

And that's it - our first tapir endpoint is exposed as an HTTP server!

## Recap

In this tutorial, we learned the basic concepts needed to bootstrap a tapir-based application:

* endpoints are defined as **values**, which describe the API. Such a description captures the **types** that are
  specified when creating the inputs/outputs.
* before exposing an endpoint, **server logic** needs to be attached to the description. It's function that transforms 
  the data extracted from the request, to data that will be used to create the response. It must match the types used
  for the inputs & outputs.
* a **server** can be started by providing basic configuration and a list of server endpoints.
