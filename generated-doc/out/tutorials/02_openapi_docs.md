# 2. Auto-generating OpenAPI docs

```{note}
The tutorial is also available [as a video](https://www.youtube.com/watch?v=rfwEJvFZT28).
```

We already know how to expose an endpoint as an HTTP server. Let's now generate documentation for the API in the
[OpenAPI](https://swagger.io/specification/) format, and expose it using the [Swagger UI](https://swagger.io).

OpenAPI is a widely used format for describing HTTP APIs, which can be serialized to JSON or YAML. Such a description can
be later used to generate client code in a given programming language, server stubs or programmer-friendly
documentation. In our case, we'll use the last option, with the help of Swagger UI, which allows both browsing and
invoking the endpoints. There are also alternative UIs, such as Redoc.

Generating the OpenAPI specification and exposing the Swagger UI might be done separately. However, we'll
use a bundle, which first interprets the provided tapir endpoints into OpenAPI and then returns another set of
endpoints, which expose the UI together with the generated specification. We'll need to add a dependency:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
```

We'll also define and expose two endpoints as an HTTP server, as described in the previous tutorial. Hence, our
starting setup of `docs.scala` is as follows:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8

import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer

@main def tapirDocs(): Unit =
  val e1 = endpoint
    .get.in("hello" / "world").in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  val e2 = endpoint
    .post.in("double").in(stringBody)
    .errorOut(stringBody)
    .out(stringBody)
    .handle { s => 
      s.toIntOption.fold(Left(s"$s is not a number"))(n => Right((n*2).toString)) 
    }

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

`NettySyncServer` is a server interpreter: it takes a list of endpoints (in our case, `List(e1, e2)`), and, basing on
their description and the server logic, exposes them as an HTTP server. Similarly, a **documentation interpreter**
takes a list of endpoints and, based on their description only (server logic is not needed here), generates the OpenAPI
documentation.

One possibility is to generate the YAML or JSON file, save it to disk, share it with other projects, etc. But in our
case, as mentioned at the beginning, we'll use the rendered specification to expose a UI, allowing browsing
our API. The UI itself needs to be exposed using HTTP. Hence, we'll need to generate tapir endpoints, which will serve
the appropriate resources (such as the UI's `index.html`, the CSS files, and the generated OpenAPI specification).

This amounts to invoking the `SwaggerInterpreter`:

```scala
val swaggerEndpoints = SwaggerInterpreter()
  .fromEndpoints[Identity](List(e1, e2), "My App", "1.0")
```

By default, the generated endpoints will expose the UI using the `/docs` path, but this can be customized using the
options.

```{note}
You might wonder what is the purpose and meaning of the `Identity` type parameter, which is used when calling the 
`fromEndpoints` method. 

Tapir integrates with multiple Scala stacks, including Pekko, Akka, cats-effect, or ZIO. We'll learn how to use them
in subsequent tutorials. They all use different types to represent effectful or asynchronous computations. So far, we've 
used direct-style, synchronous code, which doesn't use any "wrapper" types to represent computations. 

In some cases, tapir has dedicated APIs to work with direct-style, such as providing the server logic for an endpoint
using `.handle` (when using the IO effect from cats-effect, server logic is provided using the `serverLogic[IO]` 
method). In other cases, there's a single set of APIs for direct and "wrapped" style - such as the API to generate
the documentation & swagger endpoints above.

The `Identity` type constructor is a simple type alias: `type Identity[X] = X`. It can be used whenever a type 
constructor parameter (typically called `F[_]`) is required, and when we're using direct-style, meaning that 
computations run synchronously in a blocking way.
```

And that's almost all the code changes that we need to introduce! We only need to additionally expose the
`swaggerEndpoints` using our HTTP server:

{emphasize-lines="3, 5, 8, 24-25, 29"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8

import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

@main def tapirDocs(): Unit =
  val e1 = endpoint
    .get.in("hello" / "world").in(query[String]("name"))
    .out(stringBody)
    .handleSuccess(name => s"Hello, $name!")

  val e2 = endpoint
    .post.in("double").in(stringBody)
    .out(stringBody)
    .errorOut(stringBody)
    .handle { s => 
      s.toIntOption.fold(Left(s"$s is not a number"))(n => Right((n*2).toString)) 
    }

  val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[Identity](List(e1, e2), "My App", "1.0")
 
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

Browse the Swagger UI and invoke the endpoints. The generated OpenAPI specification should be available at
[`http://localhost:8080/docs/docs.yaml`](http://localhost:8080/docs/docs.yaml):

```yaml
openapi: 3.1.0
info:
  title: My App
  version: '1.0'
paths:
  /hello/world:
    get:
      operationId: getHelloWorld
      parameters:
      - name: name
        in: query
        required: true
        schema:
          type: string
      responses:
        '200':
          description: ''
          content:
            text/plain:
              schema:
                type: string
        '400':
          description: 'Invalid value for: query parameter name'
          content:
            text/plain:
              schema:
                type: string
  /double:
    post:
      operationId: postDouble
      requestBody:
        content:
          text/plain:
            schema:
              type: string
        required: true
      responses:
        '200':
          description: ''
          content:
            text/plain:
              schema:
                type: string
        default:
          description: ''
          content:
            text/plain:
              schema:
                type: string
```

This wraps up the tutorial on generating and exposing OpenAPI documentation. If you'd like to customize some of the
options, [tapir's OpenAPI reference documentation](../docs/openapi.md) should help.
