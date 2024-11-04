# Generating OpenAPI documentation

To expose documentation, endpoints first need to be interpreted into an OpenAPI yaml or json. Then, the generated
description of our API can be exposed using a UI such as Swagger or Redoc.

These two operations can be done in a single step, using the `SwaggerInterpreter` or `RedocInterpreter`. Or, if needed,
these steps can be done separately, giving you complete control over the process.

## Generating and exposing documentation in a single step

### Using Swagger

To generate OpenAPI documentation and expose it using the Swagger UI in a single step, first add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.8"
```

Then, you can interpret a list of endpoints using `SwaggerInterpreter`. The result will be a list of file-serving 
server endpoints, which use the yaml corresponding to the endpoints passed originally. These swagger endpoints, together
with the endpoints for which the documentation is generated, will need in turn to be interpreted using your server 
interpreter. For example:

```scala
import sttp.tapir.*
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.netty.{NettyFutureServerInterpreter, FutureRoute}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val myEndpoints: List[AnyEndpoint] = ???

// first interpret as swagger ui endpoints, backend by the appropriate yaml
val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[Future](myEndpoints, "My App", "1.0")

// add to your netty routes
val swaggerRoute: FutureRoute = NettyFutureServerInterpreter().toRoute(swaggerEndpoints)
```

By default, the documentation will be available under the `/docs` path. The path, as well as other options can be 
changed when creating the `SwaggerInterpreter` and invoking `fromEndpoints`. If the Swagger UI endpoints are deployed 
within a context, and you don't want Swagger to use relative paths, you'll need to set the `useRelativePaths` options
to `false`, and specify the `contextPath` one.

Moreover, model generation can be configured - see below for more details on `OpenAPIDocsOptions` and the method
parameters of `fromEndpoints`. Finally, the generated model can be customised. See the scaladocs for 
`SwaggerInterpreter`.

The swagger server endpoints can be secured using `ServerLogic.prependSecurity`, see [server logic](../server/logic.md)
for details.

### Using Redoc

Similarly as above, you'll need the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-redoc-bundle" % "1.11.8"
```

And the server endpoints can be generated using the `sttp.tapir.redoc.bundle.RedocInterpreter` class.

## Generating OpenAPI documentation separately

To generate the docs in the OpenAPI yaml format, add the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.8"
"com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "..." // see https://github.com/softwaremill/sttp-apispec
```

The case-class based model of the openapi data structures is present in the [sttp-apispec](https://github.com/softwaremill/sttp-apispec) 
project.
 
An endpoint can be converted to an instance of the model by importing the `sttp.tapir.docs.openapi.OpenAPIDocsInterpreter` 
object:

```scala
import sttp.apispec.openapi.OpenAPI
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

val booksListing = endpoint.in(path[String]("bookId"))

val docs: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0")
```

Such a model can then be refined, by adding details which are not auto-generated. Working with a deeply nested case 
class structure such as the `OpenAPI` one can be made easier by using a lens library, e.g. [Quicklens](https://github.com/adamw/quicklens).

The documentation is generated in a large part basing on [schemas](../endpoint/schemas.md). Schemas can be automatically 
derived and customised.

Quite often, you'll need to define the servers, through which the API can be reached. To do this, you can modify the
returned `OpenAPI` case class either directly or by using a helper method:

```scala
import sttp.apispec.openapi.Server

val docsWithServers: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0")
  .servers(List(Server("https://api.example.com/v1").description("Production server")))
```

Multiple endpoints can be converted to an `OpenAPI` instance by calling the method on a list of endpoints:


```scala
OpenAPIDocsInterpreter().toOpenAPI(List(addBook, booksListing, booksListingByGenre), "My Bookshop", "1.0")
```

The openapi case classes can then be serialised to YAML using [Circe](https://circe.github.io/circe/):

```scala
import sttp.apispec.openapi.circe.yaml.*

println(docs.toYaml)
```

Or to JSON:

```scala
import io.circe.Printer
import io.circe.syntax.*
import sttp.apispec.openapi.circe.*

println(Printer.spaces2.print(docs.asJson))
```

### Support for OpenAPI 3.0.3

Generating OpenAPI documentation compatible with 3.0.3 specifications is a matter of using a different encoder.
For example, generating the OpenAPI 3.0.3 YAML string can be achieved by performing the following steps:

Firstly add dependencies:
```scala
"com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.8"
"com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "..." // see https://github.com/softwaremill/sttp-apispec
```

and generate the documentation by importing valid extension methods and explicitly specifying the "3.0.3" version in the OpenAPI model:
```scala
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe.yaml.* // for `toYaml` extension method
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

case class Book(id: Option[Long], title: Option[String])

val booksListing = endpoint.in(path[String]("bookId"))

val docs: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0").openapi("3.0.3") // "3.0.3" version explicitly specified
  
println(docs.toYaml3_0_3) // OpenApi 3.0.3 YAML string would be printed to the console
```

## Exposing generated OpenAPI documentation

Exposing the OpenAPI can be done using [Swagger UI](https://swagger.io/tools/swagger-ui/) or
[Redoc](https://github.com/Redocly/redoc). You can either both interpret endpoints to OpenAPI's yaml and expose
them in a single step (see above), or you can do that separately.

The modules `tapir-swagger-ui` and `tapir-redoc` contain server endpoint definitions, which given the documentation in
yaml format, will expose it using the given context path. To use, add as a dependency either
`tapir-swagger-ui`:
```scala
"com.softwaremill.sttp.tapir" %% "tapir-swagger-ui" % "1.11.8"
```

or `tapir-redoc`:
```scala
"com.softwaremill.sttp.tapir" %% "tapir-redoc" % "1.11.8"
```

Then, you'll need to pass the server endpoints to your server interpreter. For example, using akka-http:

```scala
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.server.netty.{NettyFutureServerInterpreter, FutureRoute}
import sttp.tapir.swagger.SwaggerUI

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val myEndpoints: Seq[AnyEndpoint] = ???
val docsAsYaml: String = OpenAPIDocsInterpreter().toOpenAPI(myEndpoints, "My App", "1.0").toYaml

// add to your netty routes
val swaggerUIRoute: FutureRoute = NettyFutureServerInterpreter().toRoute(SwaggerUI[Future](docsAsYaml))
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
* `markOptionsAsNullable`: by default, optional fields are not marked as `nullable` in the OpenAPI schema. If your
  codec allows `null` values, you can explicitly specify this in documentation by changing this option.
* `schemaName`: specifies how schema names are created from the full type name. By default, this takes the last
  component of a dot-separated type name. Suffixes might be added at a later stage to disambiguate between different
  schemas with same names.
* `failOnDuplicateOperationId`: if set to `true`, the interpreter will throw an exception if it encounters two endpoints
  with the same operation id. An OpenAPI document with duplicate operation ids is not valid. Code generators can 
  silently drop duplicates. This is also verified by the [endpoint verifier](../testing.md).

## Inlined and referenced schemas

All named schemas (that is, schemas which have the `Schema.name` property defined) will be referenced at point of
use, and their definitions will be part of the `components` section. If you'd like a schema to be inlined, instead
of referenced, [modify the schema](../endpoint/schemas.md) removing the name.

## Authentication inputs and security requirements

Multiple non-optional authentication inputs indicate that all the given authentication values should be provided,
that is they will form a single security requirement, with multiple schemes, e.g.:

```scala
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.*

val multiAuthEndpoint =
  endpoint.post
    .securityIn(auth.apiKey(header[String]("token"), WWWAuthenticateChallenge("ApiKey").realm("realm")))
    .securityIn(auth.apiKey(header[String]("signature"), WWWAuthenticateChallenge("ApiKey").realm("realm")))
```

A single optional authentication method can be described by mapping to optional types, e.g. `bearer[Option[String]]`.
Hence, two security requirements will be created: an empty one, and one corresponding to the given authentication input.

If there are multiple **optional** authentication methods, they will be treated as alternatives, and separate alternative
security requirements will be created for them. However, this will not include the empty requirement, making authentication
mandatory. If authentication should be optional, an empty security requirement will be added if an `emptyAuth` input
is added (which doesn't map to any values in the request, but only serves as a marker).

```{note}
Note that even though multiple optional authentication methods might be rendered as alternatives in the documentation,
when running the server, you'll need to additionally check that at least one authentication input is provided. This 
can be done in the security logic, server logic, or by mapping the inputs using .mapDecode, as in the below example: 
```

```scala
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.*

val alternativeAuthEndpoint = endpoint.securityIn(
  // auth.apiKey(...).and(auth.apiKey(..)) will map the request headers to a tuple (Option[String], Option[String])
  auth.apiKey(header[Option[String]]("token-old"), WWWAuthenticateChallenge("ApiKey").realm("realm"))
    .and(auth.apiKey(header[Option[String]]("token-new"), WWWAuthenticateChallenge("ApiKey").realm("realm")))
    // mapping this tuple to an Either[String, String], and reporting a decode error if both values are missing
    .mapDecode {
      case (Some(oldToken), _) => DecodeResult.Value(Left(oldToken))
      case (_, Some(newToken)) => DecodeResult.Value(Right(newToken))
      case (None, None)        => DecodeResult.Missing
    } {
      case Left(oldToken)  => (Some(oldToken), None)
      case Right(newToken) => (None, Some(newToken))
    }
 )    
   
val alternativeOptionalAuthEndpoint = alternativeAuthEndpoint.securityIn(emptyAuth)
```

Finally, optional authentication inputs can be grouped into security requirements using `EndpointInput.Auth.group(String)`.
Group names are arbitrary (and aren't rendered in the documentation), but they need to be the same for a single group.
Groups should only be used on optional authentication inputs. All such inputs in a single group will become a single
security requirement when rendered in OpenAPI. As before, the fact that values for all inputs in a group are provided,
needs to be checked on the server-side, either through decoding or server logic.

## OpenAPI Specification Extensions

It's possible to extend specification with [extensions](https://swagger.io/docs/specification/openapi-extensions/).

Specification extensions can be added by first importing an extension method, and then calling the `docsExtension`
method which manipulates the appropriate attribute on the schema, endpoint or endpoint input/output:

```scala
import sttp.apispec.openapi.*
import sttp.apispec.openapi.circe.*
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*

import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.apispec.DocsExtensionAttribute.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

case class FruitAmount(fruit: String, amount: Int)

case class MyExtension(string: String, int: Int)

implicit val fruitAmountSchemaWithMyExtension: Schema[FruitAmount] =
  Schema.derived[FruitAmount].docsExtension("hello", MyExtension("world", 42))

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
object manually or using a tool such as [quicklens](https://github.com/softwaremill/quicklens).

If you are using `tapir-swagger-ui` you need to set `withShowExtensions` option for `SwaggerUIOptions`.

## Hiding inputs/outputs

It's possible to hide an input/output from the OpenAPI description using following syntax:

```scala
import sttp.tapir.*

val acceptHeader: EndpointInput[String] = header[String]("Accept").schema(_.hidden(true))
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
  case PathList("META-INF", "resources", "webjars", "swagger-ui", _*)               =>
    MergeStrategy.singleOrError
  case PathList("META-INF", _*)                                                     => MergeStrategy.discard // Optional, but usually required
  case x                                                                            =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
}
```
