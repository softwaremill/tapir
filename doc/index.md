# tapir, or Typed API descRiptions

With tapir, you can describe HTTP API endpoints as immutable Scala values. Each endpoint can contain a number of 
input parameters, error-output parameters, and normal-output parameters. An endpoint specification can be 
interpreted as:

* a server, given the "business logic": a function, which computes output parameters based on input parameters. 
  Currently supported: 
  * [Akka HTTP](server/akkahttp.md) `Route`s/`Directive`s.
  * [Http4s](server/http4s.md) `HttpRoutes[F]`
  * [Finatra](server/finatra.md) `http.Controller`
  * [Play](server/play.md) `Route`
* a client, which is a function from input parameters to output parameters.
  Currently supported:
  * [sttp](client/sttp.md).
  * [Play](client/play.md).
  * [Http4s](client/http4s.md).
* documentation. Currently supported:
  * [OpenAPI](docs/openapi.md).
  * [AsyncAPI](docs/asyncapi.md).

Tapir is licensed under Apache2, the source code is [available on GitHub](https://github.com/softwaremill/tapir).

Depending on how you prefer to explore the library, take a look at one of the [examples](examples.md) or read on
for a more detailed description of how tapir works!

Tapir is available for Scala 2.12 and 2.13 on the JVM. The client interpreter is also available for Scala.JS.

## Code teaser

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

type Limit = Int
type AuthToken = String
case class BooksFromYear(genre: String, year: Int)
case class Book(title: String)


// Define an endpoint

val booksListing: Endpoint[(BooksFromYear, Limit, AuthToken), String, List[Book], Any] = 
  endpoint
    .get
    .in(("books" / path[String]("genre") / path[Int]("year")).mapTo(BooksFromYear))
    .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    .in(header[AuthToken]("X-Auth-Token"))
    .errorOut(stringBody)
    .out(jsonBody[List[Book]])


// Generate OpenAPI documentation

import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._

val docs = OpenAPIDocsInterpreter.toOpenAPI(booksListing, "My Bookshop", "1.0")
println(docs.toYaml)


// Convert to akka-http Route

import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import akka.http.scaladsl.server.Route
import scala.concurrent.Future

def bookListingLogic(bfy: BooksFromYear,
                     limit: Limit,
                     at: AuthToken): Future[Either[String, List[Book]]] =
  Future.successful(Right(List(Book("The Sorrows of Young Werther"))))
val booksListingRoute: Route = AkkaHttpServerInterpreter
  .toRoute(booksListing)((bookListingLogic _).tupled)


// Convert to sttp Request

import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.client3._

val booksListingRequest: Request[DecodeResult[Either[String, List[Book]]], Any] = 
  SttpClientInterpreter
    .toRequest(booksListing, Some(uri"http://localhost:8080"))
    .apply((BooksFromYear("SF", 2016), 20, "xyz-abc-123"))
```

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): the Scala HTTP client you always wanted!
* sttp tapir: this project
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)

## Sponsors

Development and maintenance of sttp tapir is sponsored by [SoftwareMill](https://softwaremill.com), a software development and consulting company. We help clients scale their business through software. Our areas of expertise include backends, distributed systems, blockchain, machine learning and data analytics.

[![](https://files.softwaremill.com/logo/logo.png "SoftwareMill")](https://softwaremill.com)

# Table of contents

```eval_rst
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart
   examples
   goals

.. toctree::
   :maxdepth: 2
   :caption: Endpoints

   endpoint/basics
   endpoint/ios
   endpoint/statuscodes
   endpoint/codecs
   endpoint/customtypes
   endpoint/schemas
   endpoint/validation
   endpoint/contenttype
   endpoint/json
   endpoint/forms
   endpoint/auth
   endpoint/streaming
   endpoint/websockets
   endpoint/integrations
   endpoint/zio

.. toctree::
   :maxdepth: 2
   :caption: Server interpreters

   server/akkahttp
   server/http4s
   server/finatra
   server/play
   server/vertx
   server/options
   server/logic
   server/errors
   server/debugging

.. toctree::
   :maxdepth: 2
   :caption: Client interpreters
   
   client/sttp
   client/play
   client/http4s

.. toctree::
   :maxdepth: 2
   :caption: Documentation interpreters

   docs/openapi
   docs/asyncapi

.. toctree::
   :maxdepth: 2
   :caption: Testing

   testing

.. toctree::
   :maxdepth: 2
   :caption: Generators
   
   generator/sbt-openapi-codegen
   
.. toctree::
   :maxdepth: 2
   :caption: Other subjects

   other_interpreters
   mytapir
   troubleshooting
   contributing

