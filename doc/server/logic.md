# Server logic

The logic that should be run when an endpoint is invoked can be passed when interpreting the endpoint as a server, and
converting to a server-implementation-specific route. However, there's also the option to define an endpoint
coupled with the server logic - either given entirely or gradually, in parts. This might make it easier to work with
endpoints and their collections in a server setting.

## Defining an endpoint together with the server logic

It's possible to combine an endpoint description with the server logic in a single object,
`ServerEndpoint[I, E, O, S, F]`. Such an endpoint contains not only an endpoint of type `Endpoint[I, E, O, S]`, but
also a logic function `I => F[Either[E, O]]`, for some effect `F`.

The book example can be more concisely written as follows:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server._
import sttp.tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

val countCharactersServerEndpoint: ServerEndpoint[String, Unit, Int, Nothing, Future] =
  endpoint.in(stringBody).out(plainBody[Int]).serverLogic { s =>
    Future.successful[Either[Unit, Int]](Right(s.length))
  }

val countCharactersRoute: Route = countCharactersServerEndpoint.toRoute
```

A `ServerEndpoint` can then be converted to a route using `.toRoute`/`.toRoutes` methods (without any additional
parameters; the exact method name depends on the server interpreter), or to documentation.

Moreover, a list of server endpoints can be converted to routes or documentation as well:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

val endpoint1 = endpoint.in("hello").out(stringBody)
  .serverLogic { _ => Future.successful[Either[Unit, String]](Right("world")) }

val endpoint2 = endpoint.in("ping").out(stringBody)
  .serverLogic { _ => Future.successful[Either[Unit, String]](Right("pong")) }

val route: Route = List(endpoint1, endpoint2).toRoute
```

Note that when dealing with endpoints which have multiple input parameters, the server logic function is a function
of a *single* argument, which is a tuple; hence you'll need to pattern-match using `case` to extract the parameters:

```scala mdoc:compile-only
import sttp.tapir._
import scala.concurrent.Future

val echoEndpoint = endpoint
  .in(query[Int]("count"))
  .in(stringBody)
  .out(stringBody)
  .serverLogic { case (count, body) =>
     Future.successful[Either[Unit, String]](Right(body * count))
  }
```

### Recovering errors from failed effects

If your error type is an exception (extends `Throwable`), and if errors that occur in the server logic are represented
as failed effects, you can use a variant of the methods above, which extract the error from the failed effect, and
respond with the error output appropriately.

This can be done with the `serverLogicRecoverErrors(f: I => F[O])` method. Note that the `E` type parameter isn't
directly present here; however, the method also contains a requirement that `E` is an exception, and will only recover
errors which are subtypes of `E`. Any others will be propagated without changes.

For example:

```scala mdoc:compile-only
import sttp.tapir._
import scala.concurrent.Future

case class MyError(msg: String) extends Exception
val testEndpoint = endpoint
  .in(query[Boolean]("fail"))
  .errorOut(stringBody.map(MyError)(_.msg))
  .out(stringBody)
  .serverLogicRecoverErrors { fail =>
     if (fail) {
       Future.successful("OK") // note: no Right() wrapper
     } else {
       Future.failed(new MyError("Not OK")) // no Left() wrapper, a failed future
     }
  }
```

## Extracting common route logic

Quite often, especially for [authentication](../endpoint/auth.html), some part of the route logic is shared among 
multiple endpoints. However, these functions don't compose in a straightforward way, as authentication usually operates
on a single input, which is only a part of the whole logic's input. That's why there are two options for providing
the server logic in parts.

### Defining an extendable, base endpoint with partial server logic
 
First, you might want to define a base endpoint, with some part of the server logic already defined, and later
extend it with additional inputs/outputs, and successive logic parts.

When using this method, note that the error type must be fully defined upfront, and cannot be extended later. That's
because all of the partial logic functions can return either an error (of the given type), or a partial result.

To define partial logic for the inputs defined so far, you should use the `serverLogicForCurrent` method on an
endpoint. This accepts a method, `f: I => F[Either[E, U]]`, which given the entire (current) input defined so far,
returns either an error, or a partial result.

For example, we can create a partial server endpoint given an authentication function, and an endpoint describing
the authentication inputs:

```scala mdoc:silent
import sttp.tapir._
import sttp.tapir.server._
import scala.concurrent.Future

implicit val ec = scala.concurrent.ExecutionContext.global

case class User(name: String)
def auth(token: String): Future[Either[Int, User]] = Future {
  if (token == "secret") Right(User("Spock"))
  else Left(1001) // error code
}

val secureEndpoint: PartialServerEndpoint[User, Unit, Int, Unit, Nothing, Future] = endpoint
  .in(header[String]("X-AUTH-TOKEN"))
  .errorOut(plainBody[Int])
  .serverLogicForCurrent(auth)
```

The result is a value of type `PartialServerEndpoint`, which can be extended with further inputs and outputs, just
as a normal endpoint (except for error outputs, which are fixed). 

Successive logic parts (again consuming the entire input defined so far, and producing another partial result) can
be given by calling `serverLogicForCurrent` again. The logic can be completed - given a tuple of partial results,
and unconsumed inputs - using the `serverLogic` method:

```scala mdoc:compile-only
val secureHelloWorld1WithLogic = secureEndpoint.get
  .in("hello1")
  .in(query[String]("salutation"))
  .out(stringBody)
  .serverLogic { case (user, salutation) =>
    Future.successful[Either[Int, String]](Right(s"${salutation}, ${user.name}!"))
  }
```                    

Once the server logic is complete, a `ServerEndpoint` is returned, which can be then interpreted as a server.

The methods mentioned also have variants which recover errors from failed effects (as described above), using
`serverLogicForCurrentRecoverErrors` and `serverLogicRecoverErrors`.

### Providing server logic in parts, for an already defined endpoint

Secondly, you might already have the entire endpoint defined upfront (e.g. in a separate module, which
doesn't know anything about the server logic), and would like to provide the server logic in parts.

This can be done using the `serverLogicPart` method, which takes a function of type `f: T => F[Either[E, U]]` as
a parameter. Here `T` is some prefix of the endpoint's input `I`. The function then consumes some part of the input,
and produces an error, or a partial result.

Unlike previously, here the endpoint is considered complete, and cannot be later extended: no additional inputs
or outputs can be defined. 

```eval_rst
.. note::

  Note that the partial logic functions should be fully typed, to help with inference. It might not be possible for the
  compiler to infer, which part of the input should be consumed.
```

For example, if we have an endpoint:

```scala mdoc:silent:reset
import sttp.tapir._

val secureHelloWorld2: Endpoint[(String, String), Int, String, Nothing] = endpoint
  .in(header[String]("X-AUTH-TOKEN"))
  .errorOut(plainBody[Int])
  .get
  .in("hello2")
  .in(query[String]("salutation"))
  .out(stringBody)
```

We can provide the server logic in parts (using the same `auth` method as above):

```scala mdoc:invisible
import scala.concurrent.Future
case class User(name: String)
def auth(token: String): Future[Either[Int, User]] = ???
```

```scala mdoc:compile-only
val secureHelloWorld2WithLogic = secureHelloWorld2
  .serverLogicPart(auth)
  .andThen { case (user, salutation) =>
    Future.successful[Either[Int, String]](Right(s"$salutation, ${user.name}!"))
  }
```
 
Here, we first define a single part using `serverLogicPart`, and then complete the logic (consuming the remaining 
inputs) using `andThen`. The result is, like previously, a `ServerEndpoint`, which can be interpreted as a server.

Multiple parts can be provided using `andThenPart` invocations (consuming successive input parts). There are also
variants of the methods, which recover errors from failed effects: `serverLogicPartRecoverErrors`, 
`andThenRecoverErrors` and `andThenPartRecoverErrors`.

## Status codes

By default, successful responses are returned with the `200 OK` status code, and errors with `400 Bad Request`. However,
this can be customised by specifying how an [output maps to the status code](../endpoint/statuscodes.html).
