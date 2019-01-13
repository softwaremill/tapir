# tapir, or Typed API descRiptions

[![Join the chat at https://gitter.im/softwaremill/tapir](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/softwaremill/tapir?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/softwaremill/tapir.svg?branch=master)](https://travis-ci.org/softwaremill/tapir)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.tapir/tapir-core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.tapir/tapir-core_2.12)

With tapir you can describe HTTP API endpoints as immutable Scala values. Each endpoint can contain a number of input parameters, error-output parameters, and normal-output parameters. An endpoint specification can then be translated to:

* a server, given the "business logic": a function, which computes output parameters based on input parameters. Currently supported: 
  * [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) routes/directives.
  * [Http4s](https://http4s.org/) `HttpRoutes[F[_]]` (=`Kleisli[OptionT,Request[F],Response[F]]`)
* a client, which is a function from input parameters to output parameters. Currently supported: [sttp](https://github.com/softwaremill/sttp).
* documentation. Currently supported: [OpenAPI](https://www.openapis.org).

## Teaser

```scala
import tapir._
import tapir.json.circe._
import io.circe.generic.auto._

type Limit = Int
type AuthToken = String
case class BooksFromYear(genre: String, year: Int)
case class Book(title: String)

val booksListing: Endpoint[(BooksFromYear, Limit, AuthToken), String, List[Book]] = endpoint
    .get
    .in(("books" / path[String]("genre") / path[Int]("year")).mapTo(BooksFromYear))
    .in(query[Int]("limit").description("Maximum number of books to retrieve"))
    .in(header[String]("X-Auth-Token"))
    .errorOut(stringBody)
    .out(jsonBody[List[Book]])

//

import tapir.docs.openapi._
import tapir.openapi.circe.yaml._

val docs = booksListing.toOpenAPI("My Bookshop", "1.0")
println(docs.toYaml)

//

import tapir.server.akkahttp._
import akka.http.scaladsl.server.Route
import scala.concurrent.Future

def bookListingLogic(bfy: BooksFromYear, 
                     limit: Limit,  
                     at: AuthToken): Future[Either[String, List[Book]]] =
  Future.successful(Right(List(Book("The Sorrows of Young Werther"))))
val booksListingRoute: Route = booksListing.toRoute(bookListingLogic _)

//

import tapir.client.sttp._
import com.softwaremill.sttp._

val booksListingRequest: Request[Either[String, List[Book]], Nothing] = booksListing
  .toSttpRequest(uri"http://localhost:8080")
  .apply(BooksFromYear("SF", 2016), 20, "xyz-abc-123")
```

Also check out the [runnable example](https://github.com/softwaremill/tapir/blob/master/playground/src/main/scala/tapir/example/BooksExample.scala) available in the repository.

## Goals of the project

* programmer-friendly, human-comprehensible types, that you are not afraid to write down
* (also inferencable by IntelliJ)
* discoverable API through standard auto-complete
* separate "business logic" from endpoint definition & documentation
* as simple as possible to generate a server, client & docs
* based purely on case class-based, immutable and reusable data structures
* first-class OpenAPI support. Provide as much or as little detail as needed.
* reasonably type safe: only, and as much types to safely generate the server/client/docs

## Working with tapir

To use tapir, add the following dependency to your project:

```scala
"com.softwaremill.tapir" %% "tapir-core" % "0.0.7"
```

This will import only the core classes. To generate a server or a client, you will need to add further dependencies.

Most of tapir functionalities use package objects which provide builder and extensions methods, hence it's easiest to work with tapir if you import whole packages, e.g.:

```scala
import tapir._
```

If you don't have it already, you'll also need partial unification enabled in the compiler (alternatively, you'll need to manually provide type arguments in some cases). In sbt, this is:

```scala
scalacOptions += "-Ypartial-unification"
```

### Example Project

To see an example project using Tapir, [check out this Todo-Backend](https://github.com/hejfelix/tapir-http4s-todo-mvc) using Tapir and Http4s.

## Anatomy an endpoint

An endpoint is represented as a value of type `Endpoint[I, E, O]`, where:

* `I` is the type of the input parameters
* `E` is the type of the error-output parameters
* `O` is the type of the output parameters

Output parameters can be:

* of type `Unit`, when there's no input/ouput of the given type
* a single type
* a tuple of types

You can think of an endpoint as a function, which takes input parameters of type `I` and returns a result of type `Either[E, O]`.

### Defining an endpoint

The description of an endpoint is an immutable case class, which includes a number of methods:

* the `name`, `description`, etc. methods allow modifying the endpoint information, which will then be included in the endpoint documentation
* the `get`, `post` etc. methods specify the method using which the endpoint should support
* the `in`, `errorOut` and `out` methods allow adding a new input/output parameter
* `mapIn`, `mapInTo`, ... methods allow mapping the current input/output parameters to another value or to a case class

> An important note on mapping: in tapir, all mappings are bi-directional (they specify an isomorphism between two values). That's because each mapping can be used to generate a server or a client, as well as in many cases can be used both for input and for output.

### Defining an endpoint input/output

An input is represented as an instance of the `EndpointInput` trait, and an output as an instance of the `EndpointIO` trait (all outputs can also be used as inputs). The `tapir` package contains a number of convenience methods to define an input or an output for an endpoint. For inputs, these are:

* `path[T]`, which captures a path segment as an input parameter
* any string, which will be implicitly converted to a constant path segment. Path segments can be combined with the `/` method
* `query[T](name)` captures a query parameter with the given name
* `header[T](name)` captures a header with the given name
* `body[T, M]`, `stringBody`, `textBody[T]`, `jsonBody[T]` captures the body

For outputs, you can use the `header` and `body` family of methods.

Endpoint inputs/outputs can be combined using the `.and` method. Such a combination results in an input/output represented as a tuple of the given types. Inputs/outputs can also be mapped over, either using the `.map` method and providing mappings in both directions, or the `.mapTo` method for mapping into a case class.

### Codecs

A codec specifies how to map from and to raw textual values that are sent over the network. There are some built-in codecs for most common types such as `String`, `Int` etc. Codecs are usually defined as implicit values and resolved implicitly when they are referenced.

For example, a `query[Int]("quantity")` specifies an input parameter which will be read from the `quantity` query parameter and decoded into an `Int`. If the value cannot be parsed to an int, the endpoint won't match the request.

Optional parameters are represented as `Option` values, e.g. `header[Option[String]]("X-Auth-Token")`.

#### Media types

Codecs carry an additional type parameter, which specifies the media type. There are two built-in media types for now: `text/plain` and `application/json`.

Hence, it is possible to have a `Codec[MyCaseClass, Text]` which specified how to serialize a case class to plain text, and a different `Codec[MyCaseClass, Json]`, which specifies how to serialize a case class to json.

When defining a path, query or header parameter, only a codec with the `Text` media type can be used.

#### Schemas

A codec also contains the schema of the mapped type. This schema information is used when generating documentation. For primitive types, the schema values are built-in. For complex types, it is possible to define the schema by hand (by creating an implicit value of type `SchemaFor[T]`), however usually this will be automatically derived for case classes using [Magnolia](https://propensive.com/opensource/magnolia/).

#### Working with json

```scala
"com.softwaremill.tapir" %% "tapir-json-circe" % "0.0.7"
```

The package:

```scala
import tapir.json.circe._
```

contains codecs which, given a circe `Encoder`/`Decoder` in scope, will generate a codec using the json media type.

## Running as an akka-http server

```scala
"com.softwaremill.tapir" %% "tapir-akka-http-server" % "0.0.7"
```

To expose an endpoint as an [akka-http](https://doc.akka.io/docs/akka-http/current/) server, import the package:

```scala
import tapir.server.akkahttp._
```

This adds two extension methods to the `Endpoint` type: `toDirective` and `toRoute`. Both require the logic of the endpoint to be given as a function of type:

```scala
[I as function arguments] => Future[Either[E, O]]
```

Note that the function doesn't take the tuple `I` directly as input, but instead this is converted to a function of the appropriate arity. The created `Route`/`Directive` can then be further combined with other akka-http directives.
 
> As an endpoint can be interpreted to an akka-http directive or route, it is possible to nest and combine it with other routes. It's completely feasible that some part of the input is read using akka-http directives, and the rest using Tapir endpoint descriptions; or, that the Tapir-generated route is wrapped in e.g. a metrics route. Moreover, "edge-case endpoints", which require some special logic not expressible using Tapir, can be always implemented directly using akka-http.
  
## Using as an sttp client

```scala
"com.softwaremill.tapir" %% "tapir-sttp-client" % "0.0.7"
```

To make requests using an endpoint definition using [sttp](https://sttp.readthedocs.io), import:

```scala
import tapir.client.sttp._
```

This adds the `toRequest(Uri)` extension method to any `Endpoint` instance which, given the given base URI returns a function:

```scala
[I as function arguments] => Request[Either[E, O], Nothing]
```

After providing the input parameters, the result is a description of the request to be made, which can be further customised and sent using any sttp backend.

## Generating documentation

```scala
"com.softwaremill.tapir" %% "tapir-openapi-docs" % "0.0.7"
"com.softwaremill.tapir" %% "tapir-openapi-circe-yaml" % "0.0.7"
```

Tapir contains a case class-based model of the openapi data structures in the `openapi/openapi-model` subproject. An endpoint can be converted to an instance of the model by importing the package and calling an extension method:

```scala
import tapir.docs.openapi._
val docs = booksListing.toOpenAPI("My Bookshop", "1.0")
```

Such a model can then be refined, by adding details which are not auto-generated, or by adding whole new endpoints which are exposed by the service not through Tapir, but e.g. using a hand-written akka-http route. Working with a deeply nested case class structure such as the `OpenAPI` one can be made easier by using a lens library, e.g. [Quicklens](https://github.com/adamw/quicklens).

The openapi case classes can then be serialised, either to JSON or YAML using [Circe](https://circe.github.io/circe/):

```scala
import tapir.openapi.circe.yaml._

println(docs.toYaml)
```

## Similar projects

There's a number of similar projects from which Tapir draws inspiration:

* [endpoints](https://github.com/julienrf/endpoints)
* [typedapi](https://github.com/pheymann/typedapi~)
* [rho](https://github.com/http4s/rho)
* [typed-schema](https://github.com/TinkoffCreditSystems/typed-schema)
* [guardrail](https://github.com/twilio/guardrail)

## What's missing

* streaming support
* multi-part bodies
* file support, non-string-based bodies, form-fields bodies
* coproducts/sealed trait families/discriminators in object hierarchies
* support for OpenAPI's formats (base64, binary, email, ...)
* ... and much more :)

See the list of [issues](https://github.com/softwaremill/tapir/issues) and pick one!

## Creating your own Tapir

Tapir uses a number of packages which contain either the data classes for describing endpoints or interpreters
of this data (turning endpoints into a server or a client). Importing these packages every time you want to use Tapir
may be tedious, that's why each package object inherits all of its functionality from a trait.

Hence, it is possible to create your own object which combines all of the required functionalities and provides
a single-import whenever you want to use Tapir. For example:

```scala
object MyTapir extends Tapir
  with AkkaHttpServer
  with SttpClient
  with CirceJson
  with OpenAPICirceYaml
```

Then, a single `import MyTapir._` and all Tapir data types and extensions methods will be in scope!

## Acknowledgments

Tuple-concatenating code is copied from [akka-http](https://github.com/akka/akka-http/blob/master/akka-http/src/main/scala/akka/http/scaladsl/server/util/TupleOps.scala)

Generic derivation configuration is copied from [circe](https://github.com/circe/circe/blob/master/modules/generic-extras/src/main/scala/io/circe/generic/extras/Configuration.scala)

## Developing Tapir

Tapir is an early stage project. Everything might change. Be warned: the code is often ugly, uses mutable state, imperative loops and type casts. Luckily, there's no reflection. All suggestions welcome :)
