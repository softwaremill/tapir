# Server logic

The logic that should be run when an endpoint is invoked can be passed when interpreting the endpoint as a server, and
converting to an server-implementation-specific route. But there are other options available as well, which might make
it easier to work with collections of endpoints.

## Defining an endpoint together with the server logic

It's possible to combine an endpoint description with the server logic in a single object,
`ServerEndpoint[I, E, O, S, F]`. Such an endpoint contains not only an endpoint of type `Endpoint[I, E, O, S]`, but
also a logic function `I => F[Either[E, O]]`, for some effect `F`.

For example, the book example can be more concisely written as follows:

```scala
import sttp.tapir._
import sttp.tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

val countCharactersServerEndpoint: ServerEndpoint[String, Unit, Int, Nothing, Future] =
  endpoint.in(stringBody).out(plainBody[Int]).serverLogic { s =>
    Future.successful(Right[Unit, Int](s.length))
  }

val countCharactersRoute: Route = countCharactersServerEndpoint.toRoute
```

A `ServerEndpoint` can then be converted to a route using `.toRoute`/`.toRoutes` methods (without any additional
parameters), or to documentation.

Moreover, a list of server endpoints can be converted to routes or documentation as well:

```scala
val endpoint1 = endpoint.in("hello").out(stringBody)
  .serverLogic { _ => Future.successful("world") }

val endpoint2 = endpoint.in("ping").out(stringBody)
  .serverLogic { _ => Future.successful("pong") }

val route: Route = List(endpoint1, endpoint2).toRoute
```

Note that when dealing with endpoints which have multiple input parameters, the server logic function is a function
of a *single* argument, which is a tuple; hence you'll need to pattern-match using `case` to extract the parameters:

```scala
val echoEndpoint = endpoint
  .in(query[Int]("count"))
  .in(stringBody)
  .out(stringBody)
  .serverLogic { case (count, body) =>
     Future.successful(body * count)
  }
```

## Extracting common route logic

Quite often, especially for [authentication](../endpoint/auth.html), some part of the route logic is shared among 
multiple endpoints. However, these functions don't compose in a straightforward way, as authentication usually operates
on a single input, which is only a part of the whole logic's input. Suppose you have the following methods:

```scala
type AuthToken = String

def authFn(token: AuthToken): Future[Either[ErrorInfo, User]]
def logicFn(user: User, data: String, limit: Int): Future[Either[ErrorInfo, Result]]
```

which you'd like to apply to an endpoint with type:

```scala
val myEndpoint: Endpoint[(AuthToken, String, Int), ErrorInfo, Result, Nothing] = ...
```

To avoid composing these functions by hand, tapir defines helper extension methods, `andThenFirst` and `andThenFirstE`. 
The first one should be used when errors are represented as failed wrapper types (e.g. failed futures), the second
is errors are represented as `Either`s. 

This extension method is defined in the same traits as the route interpreters, both for `Future` (in the akka-http
interpreter) and for an arbitrary monad (in the http4s interpreter), so importing the package is sufficient to use it:

```scala
import sttp.tapir.server.akkahttp._
val r: Route = myEndpoint.toRoute((authFn _).andThenFirstE((logicFn _).tupled))
```

Writing down the types, here are the generic signatures when using `andThenFirst` and `andThenFirstE`:

```scala
f1: T => Future[U]
f2: (U, A1, A2, ...) => Future[O]
(f1 _).andThenFirst(f2): (T, A1, A2, ...) => Future[O]

f1: T => Future[Either[E, U]]
f2: (U, A1, A2, ...) => Future[Either[E, O]]
(f1 _).andThenFirstE(f2): (T, A1, A2, ...) => Future[Either[E, O]]
```

## Status codes

By default, successful responses are returned with the `200 OK` status code, and errors with `400 Bad Request`. However,
this can be customised by specifying how an [output maps to the status code](../endpoint/statuscodes.html).
