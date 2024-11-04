# Running as a Finatra server

To expose an endpoint as an [finatra](https://twitter.github.io/finatra/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-finatra-server" % "1.11.8"
```

and import the object:

```scala
import sttp.tapir.server.finatra.FinatraServerInterpreter
```

This interpreter supports the twitter `Future`.
Or, if you would like to use cats-effect project, you can add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-finatra-server-cats" % "1.11.8"
```

and import the object:

```scala
import sttp.tapir.server.finatra.cats.FinatraCatsServerInterpreter
```

This interpreter supports any effect that implements cats-effect `Effect` type.

The `toRoute` method on the interpreter requires a `ServerEndpoint`, which can be created by adding 
[server logic](logic.md) to any endpoint.

For example:

```scala
import sttp.tapir._
import sttp.tapir.server.finatra.{ FinatraServerInterpreter, FinatraRoute }
import com.twitter.util.Future

def countCharacters(s: String): Future[Either[Unit, Int]] =
  Future.value(Right[Unit, Int](s.length))

val countCharactersEndpoint: PublicEndpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: FinatraRoute = 
  FinatraServerInterpreter().toRoute(countCharactersEndpoint.serverLogic(countCharacters))
```

or a cats-effect's example:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.tapir._
import sttp.tapir.server.finatra.FinatraRoute
import sttp.tapir.server.finatra.cats.FinatraCatsServerInterpreter

def countCharacters(s: String): IO[Either[Unit, Int]] =
  IO.pure(Right[Unit, Int](s.length))

val countCharactersEndpoint: PublicEndpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])
  
def dispatcher: Dispatcher[IO] = ???
  
val countCharactersRoute: FinatraRoute = 
  FinatraCatsServerInterpreter(dispatcher).toRoute(countCharactersEndpoint.serverLogic(countCharacters))
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
