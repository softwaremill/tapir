# Generating OpenAPI documentation

To use, add the following dependencies:

```scala
"com.softwaremill.tapir" %% "tapir-openapi-docs" % "0.7.10"
"com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % "0.7.10"
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

* add `libraryDependencies += "org.webjars" % "swagger-ui" % "3.22.0"` to `build.sbt` (or newer)
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

// needed only if you use oauth2 authorization
private def redirectToOath2(query: String): Route =
    redirect(s"/swagger/oauth2-redirect.html$query", StatusCodes.PermanentRedirect)

private val swaggerVersion = {
  val p = new Properties()
  p.load(getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties"))
  p.getProperty("version")
}

val routes: Route =   
  pathPrefix("swagger") {
    pathEndOrSingleSlash {
      redirectToIndex
    } ~ path(SwaggerYml) {
      complete(yml)
    } ~ getFromResourceDirectory(s"META-INF/resources/webjars/swagger-ui/$swaggerVersion/")
  } ~
  // needed only if you use oauth2 authorization
  path("oauth2-redirect.html") { request => 
    redirectToOath2(request.request.uri.rawQueryString.map(s => '?' + s).getOrElse(""))(request)
  }
```
