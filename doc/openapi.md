# Generating OpenAPI documentation

To use, add the following dependencies:

```scala
"com.softwaremill.tapir" %% "tapir-openapi-docs" % "0.4"
"com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % "0.4"
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

The openapi case classes can then be serialised, either to JSON or YAML using [Circe](https://circe.github.io/circe/):

```scala
import tapir.openapi.circe.yaml._

println(docs.toYaml)
```

## Exposing OpenAPI documentation

Exposing the OpenAPI documentation can be very application-specific. For example, to expose the docs using the
Swagger UI and akka-http:

* add `libraryDependencies += "org.webjars" % "swagger-ui" % "3.20.9"` to `build.sbt` (or newer)
* generate the yaml content to serve as a `String` using tapir: 

```scala
import tapir.docs.openapi._
import tapir.openapi.circe.yaml._

val docsAsYaml: String = myEndpoints.toOpenAPI("My App", "1.0").toYaml
```

* add the following routes to your server:

```scala
val SwaggerYml = "swagger.yml"

private val redirectToIndex: Route =
  redirect(s"/swagger/index.html?url=/swagger/$SwaggerYml", StatusCodes.PermanentRedirect) 

val routes: Route =
  path("swagger") {
    redirectToIndex
  } ~
    pathPrefix("swagger") {
      path("") { // this is for trailing slash
        redirectToIndex
      } ~
        path(SwaggerYml) {
          complete(yml)
        } ~
        getFromResourceDirectory("META-INF/resources/webjars/swagger-ui/3.20.0/")
    }
```
