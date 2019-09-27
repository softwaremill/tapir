# Generating OpenAPI documentation

To use, add the following dependencies:

```scala
"com.softwaremill.tapir" %% "tapir-openapi-docs" % "0.11.3"
"com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % "0.11.3"
```

Tapir contains a case class-based model of the openapi data structures in the `openapi/openapi-model` subproject (the
model is independent from all other tapir modules and can be used stand-alone).
 
An endpoint can be converted to an instance of the model by importing the `tapir.docs.openapi._` package and calling 
the provided extension method:

```scala
import tapir.openapi.OpenAPI
import tapir.docs.openapi._

val docs: OpenAPI = booksListing.toOpenAPI("My Bookshop", "1.0")
```

Such a model can then be refined, by adding details which are not auto-generated. Working with a deeply nested case 
class structure such as the `OpenAPI` one can be made easier by using a lens library, e.g. [Quicklens](https://github.com/adamw/quicklens).

Multiple endpoints can be converted to an `OpenAPI` instance by calling the extension method on a list of endpoints:

```scala
List(addBook, booksListing, booksListingByGenre).toOpenAPI("My Bookshop", "1.0")
```

The openapi case classes can then be serialised, either to JSON or YAML using [Circe](https://circe.github.io/circe/):

```scala
import tapir.openapi.circe.yaml._

println(docs.toYaml)
```

Each endpoint corresponds to an operation in the OpenAPI format and should have a unique operation id. By default,
the `name` of endpoint is used as the operation id, and if this is not available, the operation id is auto-generated
by concatenating (using camel-case) the request method and path. This can be customised by providing an implicit
instance of `OpenAPIDocsOptions`.

## Exposing OpenAPI documentation

Exposing the OpenAPI documentation can be very application-specific. However, tapir contains two modules which contain
akka-http/http4s routes for exposing documentation using the swagger ui:

```scala
"com.softwaremill.tapir" %% "tapir-swagger-ui-akka-http" % "0.11.3"
"com.softwaremill.tapir" %% "tapir-swagger-ui-http4s" % "0.11.3"
```

Usage example for akka-http:

```scala
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._
import tapir.swagger.akkahttp.SwaggerAkka

val docsAsYaml: String = myEndpoints.toOpenAPI("My App", "1.0").toYaml
// add to your akka routes
new SwaggerAkka(docsAsYaml).routes
```

For http4s, use the `SwaggerHttp4s` class.
