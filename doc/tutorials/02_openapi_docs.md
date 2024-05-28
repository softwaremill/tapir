# Auto-generating OpenAPI docs

We already know how to expose an endpoint as an HTTP server. Let's now generate documentation for the API in the 
[OpenAPI](https://swagger.io/specification/) format, and expose it using the [Swagger UI](https://swagger.io).

OpenAPI is a widely used format of describing HTTP APIs, which can be serialised to JSON or YAML. Such a description can
be later used to generate client code in a given programming language, server stubs or programmer-friendly 
documentation. In our case, we'll use the last option, with the help of Swagger UI, which allows both browsing and 
invoking the endpoints. There are also alternative UIs, such as Redoc.

The process of generating the OpenAPI specification exposing the Swagger UI might be done separately. However, we'll use
a bundle, which first interprets tapir endpoints into OpenAPI, and then returns endpoints, which expose the UI together
with the generated specification. We'll need to add a dependency:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:@VERSION@
```

We'll also define two endpoints and expose them as an HTTP server, as described in the previous tutorial. Hence, our 
starting setup of `docs.scala` is as follows:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:@VERSION@
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:@VERSION@
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:@VERSION@

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def helloWorldTapir(): Unit =
  val e1 = endpoint
    .get.in("hello" / "world").in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  val e2 = endpoint
    .post.in("double").in(stringBody)
    .out(stringBody)
    .handle { s => s.toIntOption.fold(Left(s"$s is not a number"))(n => Right((n*2).toString)) }

  NettySyncServer().port(8080)
    .addEndpoints(List(e1, e2))
    .startAndWait()
```

We've got two endpoints, one corresponding to `GET /hello/world`, the other a `POST /double`. We can test them from
the command line as before:

```bash
# first console
% scala-cli docs.scala

# another console
% curl -XPOST "http://localhost:8080/double" -d "21"
42

% curl -XPOST "http://localhost:8080/double" -d "XYZ"
XYZ is not a number
```

`NettySyncServer` is a server interpreter: it takes a list of endpoints (in our case `List(e1, e2)`), and basing on 
their description and the server logic, exposes them as an HTTP server. In a similar way, a **documentation interpreter**,
takes a list of endpoints, and basing on their description only (server logic is not needed here) generates the OpenAPI
documentation.

One possibility is to generate the YAML or JSON file and save it to disk, share with other projects etc. But in our 
case, as mentioned at the beginning, we'll use the rendered specification to expose a UI, which will allow browsing
our API. The UI itself needs to be exposed using HTTP. Hence, we'll need to generate some tapir endpoints, which will
serve the appropriate resources (such as the UI's `index.html`, the CSS file, the generated OpenAPI specification).

This amounts to invoking the `SwaggerInterpreter`:

```scala
val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[Identity](List(e1, e2), "My App", "1.0")
```

By default, the generated endpoints will expose the UI using the `/docs` path, but this can be customising using the
options.

```{note}
You might wonder what is the purpose and meaning of the `Identity` type parameter, which is used when calling the 
`fromEndpoints` method. 

Tapir integrates with multiple Scala stacks, including Pekko, Akka, cats-effect or ZIO. We'll learn how to use them
in subsequent tutorials. They all use different types to represent effectful or asynchronous computations. So far, we've 
used direct-style, synchronous code, which doesn't use any "wrapper" types to represent computations. 

In some cases, tapir has dedicated APIs to work with direct-style, such as providing the server logic for an endpoint
using `.handle` (when using the IO effect from cats-effect, server logic is provided using the `serverLogic[IO]` 
method). In other cases, there's a single set of APIs both for direct and "wrapped" style - such as the API to generate
the documentation & swagger endpoints above.

The `Identity` type constructor is a simple type alias: `type Identity[X] = X`. It can be used whenever a type 
constructor parameter (typically called `F[_]`) is required, and when we're using direct-style, meaning that 
computations run synchronously, in a blocking way.
```

And that's almost all the code changes that we need to introduce! We only need to additionally expose the 
`swaggerEndpoints` using our HTTP server:

{emphasize-lines="3, 5, 20, 24"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:@VERSION@
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:@VERSION@
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:@VERSION@

import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def helloWorldTapir(): Unit =
  val e1 = endpoint
    .get.in("hello" / "world").in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  val e2 = endpoint
    .post.in("double").in(stringBody)
    .out(stringBody)
    .handle { s => s.toIntOption.fold(Left(s"$s is not a number"))(n => Right((n*2).toString)) }

  val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[Identity](List(e1, e2), "My App", "1.0")
 
  NettySyncServer().port(8080)
    .addEndpoints(List(e1, e2))
    .addEndpoints(swaggerEndpoints)
    .startAndWait()
```

Try running the code, and opening [`http://localhost:8080/docs`](http://localhost:8080/docs) in your browser:

```bash
% scala-cli docs.scala

# Now open http://localhost:8080/docs in your browser
```

Browse the Swagger UI and invoking the endpoints. The generated OpenAPI specification should be available at 
[`http://localhost:8080/docs/docs.yaml`](http://localhost:8080/docs/docs.yaml):

```yaml

```

This wraps up the tutorial on generating and exposing OpenAPI documentation. If you'd like to customise some of the
options, [tapir's OpenAPI reference documentation](../docs/openapi.md) should help.
