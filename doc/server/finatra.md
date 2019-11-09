# Running as a Finatra server

To expose an endpoint as an [finatra](https://twitter.github.io/finatra/) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-finatra-server" % "0.7.9"
```

and import the package:

```scala
import tapir.server.finatra._
```

This adds extension methods to the `Endpoint` type: `toRoute` and `toRouteRecoverErrors`. The first one
requires the logic of the endpoint to be given as a function of type (note this is a Twitter `Future`):

```scala
I => Future[Either[E, O]]
```

The second recovers errors from failed futures, and hence requires that `E` is a subclass of `Throwable` (an exception);
it expects a function of type `I => Future[O]`.

For example:

```scala
import tapir._
import tapir.server.akkahttp._
import scala.concurrent.Future
import akka.http.scaladsl.server.Route

def countCharacters(s: String): Future[Either[Unit, Int]] = 
  Future.value(Right[Unit, Int](s.length))

val countCharactersEndpoint: Endpoint[String, Unit, Int, Nothing] = 
  endpoint.in(stringBody).out(plainBody[Int])
  
val countCharactersRoute: FinatraRoute = countCharactersEndpoint.toRoute(countCharacters)
```

Note that these functions take one argument, which is a tuple of type `I`. This means that functions which take multiple 
arguments need to be converted to a function using a single argument using `.tupled`:

```scala
def logic(s: String, i: Int): Future[Either[Unit, String]] = ???
val anEndpoint: Endpoint[(String, Int), Unit, String, Nothing] = ??? 
val aRoute: FinatraRoute = anEndpoint.toRoute((logic _).tupled)
```

Now that you've created the `FinatraRoute`, add `TapirController` as a trait to your `Controller`. You can then
add the created route with `addTapirRoute`.

```scala
class MyController extends Controller with TapirController {
  addTapirRoute(endpoint.toRoute { (s: String, i: Int) => ??? }
}
```

