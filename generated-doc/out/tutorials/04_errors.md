# 4. Error handling

```{note}
The tutorial is also available [as a video](https://www.youtube.com/watch?v=iXGJsk4_2Dg).
```

Many things can go wrong: that's why error handling is often the centerpiece of software libraries. We got a glimpse of 
one of Tapir's components when it comes to error handling when we discussed 
[adding OpenAPI documentation](02_openapi_docs.md). In this tutorial, we'll investigate Tapir's approach to error 
handling in more detail.

Errors might be divided into "expected" errors: that is ones that we know how to handle, for which we have designed a 
specific response. These errors are most often caused by invalid input from the user (that is, invalid data that's part 
of the HTTP request). For such requests, we should return responses with error codes between 400 and 499, which are 
designated in the HTTP specification as "client errors".

On the other hand, there are "unexpected errors", that we didn't foresee. When they occur, they signal some kind of 
problem with the server: a bug in the server's logic, hitting a limit of requests in progress, etc. When this 
happens, we should respond with a status code between 500 and 599, and log the error for the developer to inspect. These 
are "server errors".

Which error codes exactly are returned, and what's the content of the response body that accompanies them is part of
each endpoint's description and Tapir's configuration. 

## Expected errors

As we saw in previous tutorials, the description of an endpoint is a data structure, which contains the inputs (mapped
to HTTP requests) & outputs (mapped to HTTP responses). The outputs of an endpoint describe what should happen on the
"happy path" - when the server logic succeeds. Separately, the endpoint description can contain **error outputs**, which
describe the shape of the HTTP response, in case an "expected error" occurs.

Unless specified otherwise as part of the endpoint's description, when the HTTP response is generated using the
successful outputs, the 200 status code is used; in case of error outputs, the status code is 400.

Let's define an endpoint, which returns the JSON representation of the `Result` data type in case of success, and
the JSON corresponding to the `Error` data type in case of an error. We'll be editing a `errors.scala` file. 

As in the previous tutorial, we'll be using Jsoniter to handle serialisation to JSON. We'll also need to derive the
schemas both for the `Result` and `Error` classes, to represent them properly in documentation. Let's start by 
describing the endpoint:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:1.11.8
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1

import com.github.plokhotnyuk.jsoniter_scala.macros.* 

import sttp.tapir.*
import sttp.tapir.json.jsoniter.* 

case class Result(v: Int) derives ConfiguredJsonValueCodec, Schema
case class Error(description: String) derives ConfiguredJsonValueCodec, Schema

@main def tapirErrors(): Unit = 
  val maybeErrorEndpoint = endpoint.get
    .in("test")
    .in(query[Int]("input"))
    .out(jsonBody[Result])
    .errorOut(jsonBody[Error])
```

Just as calling `.out` on an endpoint description returns an updated endpoint description, with that output added,
calling `.errorOut` returns a copy of the endpoint description, with an error output added. Each invocation of `.in`, 
`.out` and `.errorOut` accumulates inputs/outputs/error outputs.

We can now add the server logic to the endpoint, using the `.handle` method. The result of that logic has to indicate
if the result is a success, or an error. That's why the method which we'll need to provide has to return a value of type 
`Either[Error, Result]`. By convention, the left-side of an `Either` represents failure, and right-side success; we 
follow that in Tapir.

Because endpoints are fully typed, it's statically checked by the compiler that we provide a server logic with types
matching the endpoint's description; in our case, a function of type `Int => Either[Error, Result]`.

We'll also add code to expose the endpoint as a server, along with its OpenAPI documentation:

{emphasize-lines="2-3, 11-13, 24-28, 30-36"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:1.11.8
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1

import com.github.plokhotnyuk.jsoniter_scala.macros.* 

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.shared.Identity

case class Result(v: Int) derives ConfiguredJsonValueCodec, Schema
case class Error(description: String) derives ConfiguredJsonValueCodec, Schema

@main def tapirErrors(): Unit = 
  val maybeErrorEndpoint = endpoint.get
    .in("test")
    .in(query[Int]("input"))
    .out(jsonBody[Result])
    .errorOut(jsonBody[Error])
    .handle { input =>
      if input % 2 == 0
      then Right(Result(input/2))
      else Left(Error("That's an odd number!"))
    }

  val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[Identity](List(maybeErrorEndpoint), "My App", "1.0")

  NettySyncServer().port(8080)
    .addEndpoint(maybeErrorEndpoint)
    .addEndpoints(swaggerEndpoints)
    .startAndWait()
```

Let's run a couple of tests, to verify that our app does what we wanted:

```bash
% curl -v "http://localhost:8080/test?input=10"
< HTTP/1.1 200 OK
< server: tapir/1.10.9
< Content-Type: application/json
< content-length: 7
<
{"v":5}

% curl -v "http://localhost:8080/test?input=11"
< HTTP/1.1 400 Bad Request
< server: tapir/1.10.9
< Content-Type: application/json
< content-length: 39
<
{"description":"That's an odd number!"}
```

Works as designed! We get different JSONs and different status codes, depending on the result of the server logic.
Also, take a look at [the docs](http://localhost:8080/docs) - they include both response variants, with 200 and 400 
status codes.

## Unexpected errors

Every now and then an exception pops up which we forget to properly handle. In such cases, the HTTP server of course
continues to operate, but returns a 500-family response to the client. That's what happens in Tapir as well. By default,
each server contains an **exception interceptor** which returns a `500 Internal Server Error` response, and logs the
exception.

We'll extend our previous example by an occasional unhandled exception being throw from our server logic. Additionally,
we'll add Logback as a dependency, so that we get proper logging as part of the server's output. When you run the 
following, you'll see a lot of `DEBUG`-level logs (which can be turned off using `logback.xml`), but more importantly,
you'll also get `ERROR` logs when unhandled exceptions happen:

{emphasize-lines="6, 26"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:1.11.8
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1
//> using dep ch.qos.logback:logback-classic:1.5.6

import com.github.plokhotnyuk.jsoniter_scala.macros.* 

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.shared.Identity

case class Result(v: Int) derives ConfiguredJsonValueCodec, Schema
case class Error(description: String) derives ConfiguredJsonValueCodec, Schema

@main def tapirErrors(): Unit = 
  val maybeErrorEndpoint = endpoint.get
    .in("test")
    .in(query[Int]("input"))
    .out(jsonBody[Result])
    .errorOut(jsonBody[Error])
    .handle { input =>
      if input % 3 == 0 then throw new RuntimeException("Multiplies of 3 are unacceptable!")
      
      if input % 2 == 0
      then Right(Result(input/2))
      else Left(Error("That's an odd number!"))
    }

  val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[Identity](List(maybeErrorEndpoint), "My App", "1.0")

  NettySyncServer().port(8080)
    .addEndpoint(maybeErrorEndpoint)
    .addEndpoints(swaggerEndpoints)
    .startAndWait()
```

Trying to invoke the endpoint results in a 500 status code:

```bash
% curl -v "http://localhost:8080/test?input=9"

< HTTP/1.1 500 Internal Server Error
< server: tapir/1.10.9
< Content-Type: text/plain; charset=UTF-8
< content-length: 21
<
Internal server error
```

And in the logs, we get the full details on what went wrong:

```
16:18:14.355 [virtual-41] ERROR sttp.tapir.server.netty.sync.NettySyncServerOptions$ -- Exception when handling request: GET /test?input=9, by: GET /test, took: 18ms
java.lang.RuntimeException: Multiplies of 3 are unacceptable!
	at errors$package$.$anonfun$15(errors.scala:26)
```

## Further reading

There's still a lot to cover on error handling in Tapir, and we'll go into more detail on some of the options in 
subsequent tutorials. For the impatient, you might be interested in the following reference documentation sections:

* [error handling in server interpreters](../server/errors.md)
* [one-of outputs](../endpoint/oneof.md)
* [inputs/outputs, section on status codes](../endpoint/ios.md)
