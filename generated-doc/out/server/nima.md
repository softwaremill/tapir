# Running as a Helidon Níma server

```eval_rst
.. note::
  Helidon Níma requires JDK supporting Project Loom threading (JDK21 or newer).
```

To expose an endpoint as a [Helidon Níma](https://helidon.io/nima) server, first add the following 
dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-nima-server" % "1.9.6"
```

Loom-managed concurrency uses direct style instead of effect wrappers like `Future[T]` or `IO[T]`. Because of this,
Tapir endpoints defined for Nima server use `Id[T]`, which provides compatibility, while effectively means just `T`.

Such endpoints are then processed through `NimaServerInterpreter` in order to obtain an `io.helidon.webserver.http.Handler`:

```scala
import io.helidon.webserver.WebServer
import sttp.tapir._
import sttp.tapir.server.nima.{Id, NimaServerInterpreter}

val helloEndpoint = endpoint.get
  .in("hello")
  .out(stringBody)
  .serverLogicSuccess[Id] { _ =>
    Thread.sleep(1000)
    "hello, world!"
  }

val handler = NimaServerInterpreter().toHandler(List(helloEndpoint))

WebServer
  .builder()
  .routing { builder =>
    builder.any(handler)
    ()
  }
  .port(8080)
  .build()
  .start()
```
