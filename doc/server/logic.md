# Server logic

To interpret a single endpoint, or multiple endpoints as a server, the endpoint descriptions must be coupled
with functions which implement the server logic. The shape of these functions must match the types of the inputs
and outputs of the endpoint.

The type of such an endpoint+logic combination is `ServerEndpoint[R, F]`, where `R` are the endpoint's requirements
(websockets, streams) and `F` is the effect type of the logic, such as `Future` or `IO`. If you'd like to preserve
the full type information of the inputs and outputs, you can use the `ServerEndpoint.Full[A, U, I, E, O, R, F]` type 
alias.

For public endpoints (where the type of the security inputs is `Unit`), the server logic can be provided using the
`serverLogic(f: I => F[Either[E, O]]` method. For secure endpoints, you first need to provide the security logic
using `serverSecurityLogic` and then the main logic.

Hence, apart from a `Endpoint[A, I, E, O, R]`, the server endpoint contains:

* the server logic of type `I => F[Either[E, O]]` for public endpoint
* the security logic of type `A => F[Either[E, U]]` and the main logic of type `U => I => F[Either[E, O]]`

The intuition behind the `A` and `U` types is that the first is the type of authentication data, and the second of the
"user" (whatever this might mean in your system), that is found provided that the authentication was successful.

If either the security logic, or the main logic fails, an error of type `E` might be returned.

## Multiple input parameters

Note that when dealing with endpoints which have multiple input parameters, the server logic function is a function
of a *single* argument `I`, which is a tuple. This means that functions which take multiple arguments need to be 
converted to a function using a single argument using `.tupled`, or that you'll need to pattern-match using `case` 
to extract the parameters:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future

// using case:
val echoEndpoint = endpoint
  .in(query[Int]("count"))
  .in(stringBody)
  .out(stringBody)
  .serverLogic { case (count, body) =>
     Future.successful[Either[Unit, String]](Right(body * count))
  }
  
// using .tupled:
def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: PublicEndpoint[(String, Int), Unit, String, Any] = ??? 
val aServerEndpoint: ServerEndpoint[Any, Future] = anEndpoint.serverLogic((logic _).tupled)
```

## Interpreting as a server

Both a single server endpoint, and multiple endpoints can be interpreted as a server. As an example, a list of server 
endpoints can be converted to an akka-http route:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

val endpoint1 = endpoint.in("hello").out(stringBody)
  .serverLogic { _ => Future.successful[Either[Unit, String]](Right("world")) }

val endpoint2 = endpoint.in("ping").out(stringBody)
  .serverLogic { _ => Future.successful[Either[Unit, String]](Right("pong")) }

val route: Route = AkkaHttpServerInterpreter().toRoute(List(endpoint1, endpoint2))
```

## Recovering errors from failed effects

If your `E` error type is an exception (extends `Throwable`), and if errors that occur in the server logic are 
represented as failed effects, you can use a variant of the methods above, which extract the error from the failed 
effect, and respond with the error output appropriately.

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

## Other server logic variants

There are also other variants of the methods that can be used to provide the server logic:

* `serverLogicSuccess(f: I => F[O])`: specialized to the case, when the result is always a success (no errors are 
  possible)
* `serverLogicError(f: I => F[E])`: similarly for endpoints which always return an error
* `serverLogicPure(f: I => Either[E, O])`: if the server logic function is pure, that is returns a strict value, not
  a description of side-effects

Similar variants are available to provide the security logic. 

## Re-usable security logic

Quite often the security logic is shared among multiple endpoints. For secure endpoints, which have
[security inputs](../endpoint/security.md) defined, the security logic needs to be provided first, followed by the
main logic.

This can be done either on a complete endpoint, where all of the inputs/outputs are provided, as a two-step process.

Alternatively, a base "secure" endpoint can be defined, with the security inputs and security logic provided. As this
is an immutable value, such an endpoint can be then extended multiple times, by adding more regular inputs and outputs, 
each time yielding a new immutable representation. For each such extension, the main server logic still needs to be 
provided.

For example, we can create a partial server endpoint given the security logic, and an endpoint with security inputs:

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

val secureEndpoint: PartialServerEndpoint[String, User, Unit, Int, Unit, Any, Future] = endpoint
  .securityIn(header[String]("X-AUTH-TOKEN"))
  .errorOut(plainBody[Int])
  .serverSecurityLogic(auth)
```

The result is a value of type `PartialServerEndpoint`, which can be extended with further inputs and outputs, just
as a normal endpoint (except for error outputs, which are fixed). 

Then, we can complete the endpoint to a `ServerEndpoint` by providing the main server logic using `.serverLogic` or
any of the other variants:

```scala mdoc:compile-only
val secureHelloWorld1WithLogic: ServerEndpoint[Any, Future] = secureEndpoint.get
  .in("hello1")
  .in(query[String]("salutation"))
  .out(stringBody)
  .serverLogicSuccess { (user: User) => (salutation: String) =>
    Future.successful(s"${salutation}, ${user.name}!")
  }
```

## Status codes

By default, successful responses are returned with the `200 OK` status code, and errors with `400 Bad Request`. However,
this can be customised by using a [status code output](../endpoint/ios.md).
