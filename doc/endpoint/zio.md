# ZIO integration

The `tapir-zio` module defines type aliases and extension methods which make it more ergonomic to work with 
[ZIO](https://zio.dev) and tapir. Moreover, the `tapir-zio-http4s-server` contains similar extensions useful when
exposing the endpoints using the [http4s](https://http4s.org) server.

You'll need the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio" % "@VERSION@"
"com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server" % "@VERSION@"
```

Next, instead of the usual `import sttp.tapir._`, you should import:

```scala mdoc:compile-only
import sttp.tapir.ztapir._
```

This brings into scope all of the [basic](basics.md) input/output descriptions, which can be used to define an endpoint. 
Additionally, it defines the `ZEndpoint` type alias, which should be used instead of `Endpoint`.

```eval_rst
.. note::

  You should have only one of these imports in your source file. Otherwise, you'll get naming conflicts. The
  ``import sttp.tapir.ztapir._`` import is meant as a complete replacement of ``import sttp.tapir._``.
```

## Server logic

When defining the business logic for an endpoint, the following methods are available, which replace the 
[standard ones](../server/logic.md):

* `def zServerLogic(logic: I => ZIO[R, E, O]): ZServerEndpoint[R, I, E, O]`
* `def zServerLogicPart(logicPart: T => ZIO[R, E, U])`
* `def zServerLogicForCurrent(logicPart: I => ZIO[R, E, U])`

The first defines complete server logic, while the second and third allow defining server logic in parts.

## Exposing endpoints using the http4s server

To bring into scope the extension methods used to interpret a `ZServerEndpoint` as a http4s server, add the following
import:

```scala mdoc:compile-only
import sttp.tapir.server.http4s.ztapir._
```

This adds the following methods on `ZEndpoint`:

* `def toRoutes(logic: I => ZIO[Any, E, O]): HttpRoutes[Task]`
* `def toRoutesR(logic: I => ZIO[R, E, O]): URIO[R, HttpRoutes[Task]]` (when the logic requires an environment)

And the following methods on `ZServerEndpoint` or `List[ZServerEndpoint]`: 

* `def toRoutes: HttpRoutes[Task]`
* `def toRoutesR: URIO[R, HttpRoutes[Task]]` (when the logic requires an environment)

## Example

Two examples of using the ZIO integration are available. The first showcases basic functionality, while the second shows how to use partial server logic methods:

* [ZIO basic example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioExampleHttp4sServer.scala)
* [ZIO partial server logic example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioPartialServerLogicHttp4s.scala)