# Generating OpenAPI documentation

## Generating and exposing documentation in a single step

### Using Swagger

To generate OpenAPI documentation and expose it using the Swagger UI in a single step, first add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "0.20.0-M9"
```

Then, you can interpret a list of endpoints, as server endpoints exposing the Swagger UI, using `SwaggerInterpreter`. 
The result - a list of file-serving endpoints - will be configured to use the yaml corresponding to the passed 
endpoints. The swagger endpoints will need in turn to be interpreted using your server interpreter. For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.Future

val myEndpoints: List[AnyEndpoint] = ???

// first interpret as swagger ui endpoints, backend by the appropriate yaml
val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[Future](myEndpoints, "My App", "1.0")

// add to your akka routes
val swaggerRoute = AkkaHttpServerInterpreter().toRoute(swaggerEndpoints)
```

By default, the documentation will be available under the `/docs` path. The path, as well as other options can be 
changed when creating the `SwaggerInterpreter` and invoking `fromEndpoints`. If the swagger endpoints are deployed 
within a context, this information needs to be passed to the interpreter, to create proper redirects. 

Moreover, model generation can be configured - see below for more details on `OpenAPIDocsOptions` and the method
parameters of `fromEndpoitns`. Finally, the generated model can be customised. See the scaladocs for 
`SwaggerInterpreter`.

The swagger server endpoints can be secured using `ServerLogic.prependSecurity`, see [server logic](../server/logic.md)
for details.

### Using Redoc

Similarly as above, you'll need the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-redoc-bundle" % "0.20.0-M9"
```

And the server endpoints can be generated using the `sttp.tapir.redoc.bundle.RedocInterpreter` class.

## Generating OpenAPI documentation

To generate the docs in the OpenAPI yaml format, add the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "@VERSION@"
"com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % "@VERSION@"
```

Tapir contains a case class-based model of the openapi data structures in the `openapi/openapi-model` subproject (the
model is independent from all other tapir modules and can be used stand-alone).
 
An endpoint can be converted to an instance of the model by importing the `sttp.tapir.docs.openapi.OpenAPIDocsInterpreter` 
object:

```scala mdoc:silent
import sttp.tapir._
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

val booksListing = endpoint.in(path[String]("bookId"))

val docs: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0")
```

Such a model can then be refined, by adding details which are not auto-generated. Working with a deeply nested case 
class structure such as the `OpenAPI` one can be made easier by using a lens library, e.g. [Quicklens](https://github.com/adamw/quicklens).

The documentation is generated in a large part basing on [schemas](../endpoint/codecs.md#schemas). Schemas can be
[automatically derived and customised](../endpoint/schemas.md).

Quite often, you'll need to define the servers, through which the API can be reached. To do this, you can modify the
returned `OpenAPI` case class either directly or by using a helper method:

```scala mdoc:silent
import sttp.tapir.openapi.Server

val docsWithServers: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0")
  .servers(List(Server("https://api.example.com/v1").description("Production server")))
```

Multiple endpoints can be converted to an `OpenAPI` instance by calling the method on a list of endpoints:

```scala mdoc:invisible
val addBook = endpoint.in(path[String]("bookId"))
val booksListingByGenre = endpoint.in(path[String]("genre"))
```

```scala mdoc:silent
OpenAPIDocsInterpreter().toOpenAPI(List(addBook, booksListing, booksListingByGenre), "My Bookshop", "1.0")
```

The openapi case classes can then be serialised to YAML using [Circe](https://circe.github.io/circe/):

```scala mdoc:silent
import sttp.tapir.openapi.circe.yaml._

println(docs.toYaml)
```

Or to JSON:

```scala mdoc:silent
import io.circe.Printer
import io.circe.syntax._
import sttp.tapir.openapi.circe._

println(Printer.spaces2.print(docs.asJson))
```

## Options

Options can be customised by providing an instance of `OpenAPIDocsOptions` to the interpreter:

* `operationIdGenerator`: each endpoint corresponds to an operation in the OpenAPI format and should have a unique 
  operation id. By default, the `name` of endpoint is used as the operation id, and if this is not available, the 
  operation id is auto-generated by concatenating (using camel-case) the request method and path.
* `defaultDecodeFailureOutput`: if an endpoint does not define a Bad Request response in `errorOut`,
  tapir will try to guess if decoding of inputs may fail, and add a 400 response if necessary.
  You can override this option to customize the mapping of endpoint's inputs to a default error response.
  If you'd like to disable this feature, just provide a function that always returns `None`:
  ```scala
  OpenAPIDocsOptions.default.copy(defaultDecodeFailureOutput = _ => None)
  ```

## Inlined and referenced schemas

All named schemas (that is, schemas which have the `Schema.name` property defined) will be referenced at point of
use, and their definitions will be part of the `components` section. If you'd like a schema to be inlined, instead
of referenced, [modify the schema](../endpoint/schemas.md) removing the name.

## OpenAPI Specification Extensions

It's possible to extend specification with [extensions](https://swagger.io/docs/specification/openapi-extensions/).

Specification extensions can be added by first importing an extension method, and then calling the `docsExtension`
method which manipulates the appropriate attribute on the endpoint / endpoint input/output:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import sttp.tapir.openapi._
import sttp.tapir.openapi.circe._
import sttp.tapir.openapi.circe.yaml._
import io.circe.generic.auto._

import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.apispec.DocsExtensionAttribute._

case class FruitAmount(fruit: String, amount: Int)

case class MyExtension(string: String, int: Int)

val sampleEndpoint =
  endpoint.post
    .in("path-hello" / path[String]("world").docsExtension("x-path", 22))
    .in(query[String]("hi").docsExtension("x-query", 33))
    .in(jsonBody[FruitAmount].docsExtension("x-request", MyExtension("a", 1)))
    .out(jsonBody[FruitAmount].docsExtension("x-response", List("array-0", "array-1")).docsExtension("x-response", "foo"))
    .errorOut(stringBody.docsExtension("x-error", "error-extension"))
    .docsExtension("x-endpoint-level-string", "world")
    .docsExtension("x-endpoint-level-int", 11)
    .docsExtension("x-endpoint-obj", MyExtension("42.42", 42))

val rootExtensions = List(
  DocsExtension.of("x-root-bool", true),
  DocsExtension.of("x-root-list", List(1, 2, 4))
)

val openAPIYaml = OpenAPIDocsInterpreter().toOpenAPI(sampleEndpoint, Info("title", "1.0"), rootExtensions).toYaml
```

However, to add extensions to other unusual places (like, `License` or `Server`, etc.) you should modify the `OpenAPI`
object manually or using f.e. [Quicklens](https://github.com/softwaremill/quicklens)

## Exposing generated OpenAPI documentation

Exposing the OpenAPI can be done using [Swagger UI](https://swagger.io/tools/swagger-ui/) or 
[Redoc](https://github.com/Redocly/redoc). You can either both interpret endpoints to OpenAPI's yaml and expose
them in a single step (see above), or you can do that separately.

The modules `tapir-swagger-ui` and `tapir-redoc` contain server endpoint definitions, which given the documentation in 
yaml format, will expose it using the given context path. To use, add as a dependency either 
`tapir-swagger-ui`:
```scala
"com.softwaremill.sttp.tapir" %% "tapir-swagger-ui" % "@VERSION@"
```

or `tapir-redoc`:
```scala
"com.softwaremill.sttp.tapir" %% "tapir-redoc" % "@VERSION@"
```

Then, you'll need to pass the server endpoints to your server interpreter. For example, using akka-http:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.SwaggerUI

import scala.concurrent.Future

val myEndpoints: Seq[AnyEndpoint] = ???
val docsAsYaml: String = OpenAPIDocsInterpreter().toOpenAPI(myEndpoints, "My App", "1.0").toYaml

// add to your akka routes
val swaggerUIRoute = AkkaHttpServerInterpreter().toRoute(SwaggerUI[Future](docsAsYaml))
```

## Using SwaggerUI with sbt-assembly

The `tapir-swagger-ui` and `tapir-swagger-ui-bundle` modules rely on a file in the `META-INF` directory tree, to 
determine the version of the Swagger UI.  You need to take additional measures if you package your application with 
[sbt-assembly](https://github.com/sbt/sbt-assembly) because the default merge strategy of the `assembly` task discards 
most artifacts in that directory. To avoid a `NullPointerException`, you need to include the following file explicitly:

```scala
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") =>
    MergeStrategy.singleOrError
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
```
