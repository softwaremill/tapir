# tapir, or Typed API descRiptions

With tapir you can describe your HTTP API endpoints as immutable Scala values. Each endpoint has a number of input parameters, error-output parameters, and  normal-output parameters. Such an endpoint specification can then be translated to:

* a server, given the "business logic": a function, which computes output parameters based on input parameters. Currently supported: [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) routes/directives.
* a client, which is a function from the input parameters, to the output parameters. Currently supported: [sttp](https://github.com/softwaremill/sttp).
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
    .in(query[Int]("limit").description("Maximum number of products to retrieve"))
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

def bookListingLogic(bfy: BooksFromYear, limit: Limit, at: AuthToken): Future[Either[String, List[Book]]] =
  Future.successful(Right(List(Book("The Sorrows of Young Werther"))))
val booksListingRoute: Route = booksListing.toRoute(bookListingLogic _)

//

import tapir.client.sttp._
import com.softwaremill.sttp._

val booksListingRequest: Request[Either[String, List[Book]], Nothing] = booksListing
  .toSttpRequest(uri"http://localhost:8080")
  .apply(BooksFromYear("SF", 2016), 20, "xyz-abc-123")
```

## Goals of the project

* programmer-friendly, human-comprehensible types, that you are not afraid to write down
* (also inferencable by IntelliJ)
* discoverable API through standard auto-complete
* separate "business logic" from endpoint definition & documentation
* as simple as possible to generate a server, client & docs
* based purely on case class-based datastructures
* first-class OpenAPI support. Provide as much or as little detail as needed.
* reasonably type safe: only, and as much types to safely generate the server/client/docs

## Working with tapir

To use tapir, add the following dependency to your project:

TODO

This will import only the core case classes. To generate a server or a client, you will need to add further dependencies.

Most of tapir functionalities use package objects which provide builder and extensions methods, hence it's easiest to work with tapir if you import whole packages, e.g.:

```scala
import tapir._
```

## Anatomy an en endpoint

An endpoint is represented as a value of type `Endpoint[I, E, O]`, where:

* `I` is the type of the input parameters
* `E` is the type of the error-output parameters
* `O` is the type of the output parameters

Output parameters can be:

* of type `Unit`, when there's no input/ouput of the given type
* a single type
* a tuple of tupes

You can think of an endpoint as a function, which takes input parameters of type `I` and returns a result of type `Either[E, O]`.

### Defining an endpoint

The description of an endpoint is an immutable case class, which includes a number of methods:

* the `name`, `description`, etc. methods allow modifying the endpoint information, which will then be included in the endpoint documentation
* the `get`, `post` etc. methods specify the method which the endpoint uses
* the `in`, `errorOut` and `out` methods allow adding a new input/output parameter
* `mapIn`, `mapInTo`, ... methods allow mapping the current input/output parameters to another value or two a case class

> An important note on mapping: in tapir, all mappings are bi-directional (they specify in fact an isomorphism between two values). That's because each mapping can be used to generate a server or a client, as well as in many cases can be used both for input and for output.

### Defining an endpoint input/output

The `tapir` package contains a number of methods to define an input or an output for an endpoint. For inputs, these are:

* `path[T]`, which captures a path segment as an input parameter
* any string, which will be implicitly converted to a constant path segment. Path segments can be combined with the `/` method
* `query[T](name)` captures a query parameter with the given name
* `header[T](name)` captures a header with the given name
* `body[T, M]`, `stringBody`, `textBody[T]`, `jsonBody[T]` captures the body

For outputs, you can use the `header` and `body` family of methods.

Endpoint input/outputs can be combined using the `.and` method. Such a combination specified a parameter which contains a tuple of the given types. Inputs/outputs can also be mapped over, either using the `.map` method and providing mappings in both directions, or the convenience `.mapTo` method for mapping into a case class.

### Type mappers

A type mapper specifies how to map from and to raw textual value that are sent over the network. There are some built-in type mappers for most common types such as `String`, `Int` etc. Type mappers are usually defined as implicit values and resolved implicitly when they are referenced.

For example, a `query[Int]("quantity")` specifies an input parameter which will be read from the `quantity` query parameter and mapped into an `Int`. If the value cannot be parsed to an int, the endpoint won't match the request.

Optional parameters are represented as `Option` values, e.g. `header[Option[String]]("X-Auth-Token")`.

#### Media types

Type mappers carry an additional type parameter, which specified the media type. There are two built-in media types for now: `text/plain` and `application/json`.

Hence, it is possible to have a `TypeMapper[MyCaseClass, Text]` which specified how to serialize a case class to plain text, and a different `TypeMapper[MyCaseClass, Json]`, which specifies how to serialize a case class to json.

#### Schemas

A type mapper also contains the schema of the mapped type. This schema information is used when generating documentation. For primitive types, the schema values are built-in. For complex types, it is possible to define the schema by hand (by creating a value of type `Schema`), however usually this will be automatically derived for case classes using [Magnolia](https://propensive.com/opensource/magnolia/).

#### Working with json

The package:

```scala
import tapir.json.circe._
```

contains type mappers which, given a circe `Encoder`/`Decoder` in scope, will generate a type mapper using the json media type.

## Running as an akka-http server

To expose an endpoint as an [akka-http](https://doc.akka.io/docs/akka-http/current/) server, import the package:

```scala
import tapir.server.akkahttp._
```

This adds two extension methods to the `Endpoint` type: `toDirective` and `toRoute`. Both require the logic of the endpoint to be given as a function of type:

```scala
[I as function arguments] => Future[Either[E, O]]
```

Note that the function doesn't take the tuple `I` directly as input, but instead this is converted to a function of the appropriate arity. The created `Route`/`Directive` can then be further combined with other akka-http directives.

## Using as an sttp server

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

Tapir contains a case class-based model of the openapi data structure in the `openapi/openapi-model` subproject. An endpoint can be converted to the model by importing the package and calling an extension method:

```scala
import tapir.docs.openapi._
val docs = booksListing.toOpenAPI("My Bookshop", "1.0")
```

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

## Creating your own Tapir

Tapir uses a number of packages which contain either the data classes for describing endpoints or interpreters
of this data (turning endpoints into a server or a client). Importing these packages every time you want to use Tapir
may be tedious, that's why each package object inherits all of its functionality from a trait.

Hence, it is possible to create your own object which combines all of the required functionalities and provides
a single-import whenever you want to use Tapir. For example:

```scala
object MyTapir extends AkkaHttpServer
  with SttpClient
  with CirceJson
  with OpenAPICirceYaml
```

## Acknowledgments

Tuple-concatenating code is copied from [akka-http](https://github.com/akka/akka-http/blob/master/akka-http/src/main/scala/akka/http/scaladsl/server/util/TupleOps.scala)

## Developing Tapir

Tapir is an early stage project. Everything might change. Be warned: the code is often ugly, uses mutable state, imperative loops and type casts. Luckily, there's no reflection. All suggestions welcome :)