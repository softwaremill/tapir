# Running as a Finatra server

To expose an endpoint as an [finatra](https://twitter.github.io/finatra/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-finatra-server" % "0.18.0-M2"
```

and import the object:

```scala
import sttp.tapir.server.finatra.FinatraServerInterpreter
```

or if you would like to use cats-effect project, you can add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-finatra-server-cats" % "0.18.0-M2"
```

and import the object:

```scala
import sttp.tapir.server.finatra.cats.FinatraCatsServerInterpreter
```

The interpreter objects contain the `toRoute` and `toRouteRecoverErrors` method. The first one
requires the logic of the endpoint to be given as a function of type (note this is a Twitter `Future` or a cats-effect project's `Effect` type):

```scala
I => Future[Either[E, O]]
```

or giben an effect type that supports cats-effect's `Effect[F]` type, for example, project 
[Catbird's](https://github.com/travisbrown/catbird) `Rerunnbale`:

```scala
I => Rerunnable[Either[E, O]]
```

The second recovers errors from failed futures, and hence requires that `E` is a subclass of `Throwable` (an exception);
it expects a function of type `I => Future[O]` or type `I => F[O]` if `F` supports cat-effect's `Effect` type.

For example:

```scala
import sttp.tapir._
import sttp.tapir.server.finatra.{ FinatraServerInterpreter, FinatraRoute }
import com.twitter.util.Future

def countCharacters(s: String): Future[Either[Unit, Int]] =
  Future.value(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: FinatraRoute = FinatraServerInterpreter.toRoute(countCharactersEndpoint)(countCharacters)
```

or a cats-effect's example:

```scala
import cats.effect.IO
import sttp.tapir._
import sttp.tapir.server.finatra.FinatraRoute
import sttp.tapir.server.finatra.cats.FinatraCatsServerInterpreter

def countCharacters(s: String): IO[Either[Unit, Int]] =
  IO.pure(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: FinatraRoute = FinatraCatsServerInterpreter.toRoute(countCharactersEndpoint)(countCharacters)
```

Note that the second argument to `toRoute` is a function with one argument, a tuple of type `I`.  This means that 
functions which take multiple arguments need to be converted to a function using a single argument using `.tupled`:

```scala
import sttp.tapir._
import sttp.tapir.server.finatra.{ FinatraServerInterpreter, FinatraRoute }
import com.twitter.util.Future

def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Any] = ???
val aRoute: FinatraRoute = FinatraServerInterpreter.toRoute(anEndpoint)((logic _).tupled)
```

Now that you've created the `FinatraRoute`, add `TapirController` as a trait to your `Controller`. You can then
add the created route with `addTapirRoute`.

```scala
import sttp.tapir.server.finatra._
import com.twitter.finatra.http.Controller

val aRoute: FinatraRoute = ???
class MyController extends Controller with TapirController {
  addTapirRoute(aRoute)
}
```

## Defining an endpoint together with the server logic

It's also possible to define an endpoint together with the server logic in a single, more concise step. See
[server logic](logic.md) for details.