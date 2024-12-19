# tapir

<div style="text-align: center">
<p>Rapid development of self-documenting APIs</p>
<img src="https://github.com/softwaremill/tapir/raw/master/doc/logo.png" alt="tapir" height="100" width="100" />
</div>

## Intro

Tapir is a library to describe HTTP APIs, expose them as a server, consume as a client, and automatically document 
using open standards. 

Tapir is fast and developer-friendly. The endpoint definition APIs are crafted with readability and discoverability in
mind. Our Netty-based server is one of the best-performing Scala HTTP servers available.

```scala
endpoint
  .get.in("hello").in(query[String]("name"))
  .out(stringBody)
  .handleSuccess(name => s"Hello, $name!")
```

Tapir integrates with all major Scala stacks, so you can use your favorite approach to Functional Programming, while
leveraging all the benefits that Tapir brings! 

Seamless integration with the Scala and HTTP ecosystems is one of Tapir's major strengths:

* all popular Scala HTTP server implementations are supported. You can define your entire API using Tapir, or expose 
Tapir-managed routes alongside "native" ones. This is especially useful when gradually adopting Tapir, or using it for 
selected use-cases. 
* the Scala ecosystem is rich with libraries leveraging its type-safety and enhancing the developer's toolbox, 
that's why Tapir provides integrations with many of such custom type, JSON and observability libraries 
* documentation can be generated in the [OpenAPI](docs/openapi.md), [AsyncAPI](docs/asyncapi.md) and [JSON Schema](docs/json-schema.md) formats 

Depending on how you'd prefer to explore Tapir, this documentation has three main sections:

1. There's a number of [tutorials](tutorials/01_hello_world.md), which provide a gentle introduction to the library
2. Nothing compares to tinkering with working code, that's why we've prepared [runnable examples](examples.md),
   covering solutions to many "everyday" problems 
3. Finally, the reference documentation describes all of Tapir's aspects in depth - take a look at the menu on
   the left, starting with the "Endpoints" section

ScalaDocs are available at [javadoc.io](https://www.javadoc.io/doc/com.softwaremill.sttp.tapir). 

Tapir is licensed under Apache2, the source code is [available on GitHub](https://github.com/softwaremill/tapir).

## Why tapir?

* **type-safety**: compile-time guarantees, develop-time completions, read-time information
* **declarative**: separate the shape of the endpoint (the "what"), from the server logic (the "how")
* **OpenAPI / Swagger integration**: generate documentation from endpoint descriptions
* **observability**: leverage the metadata to report rich metrics and tracing information
* **abstraction**: re-use common endpoint definitions, as well as individual inputs/outputs
* **library, not a framework**: integrates with your stack

## Code teaser

```scala
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

type Limit = Int
type AuthToken = String
case class BooksQuery(genre: String, year: Int)
case class Book(title: String)


// Define an endpoint

val booksListing: PublicEndpoint[(BooksQuery, Limit, AuthToken), String, List[Book], Any] =
  endpoint
    .get
    .in(("books" / path[String]("genre") / path[Int]("year")).mapTo[BooksQuery])
    .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    .in(header[AuthToken]("X-Auth-Token"))
    .errorOut(stringBody)
    .out(jsonBody[List[Book]])


// Generate OpenAPI documentation

import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

val docs = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0")
println(docs.toYaml)


// Convert to pekko-http Route

import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter
import org.apache.pekko.http.scaladsl.server.Route
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

def bookListingLogic(bfy: BooksQuery,
                     limit: Limit,
                     at: AuthToken): Future[Either[String, List[Book]]] =
  Future.successful(Right(List(Book("The Sorrows of Young Werther"))))

val booksListingRoute: Route = PekkoHttpServerInterpreter()
  .toRoute(booksListing.serverLogic((bookListingLogic _).tupled))


// Convert to sttp Request

import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.client3.*

val booksListingRequest: Request[DecodeResult[Either[String, List[Book]]], Any] =
  SttpClientInterpreter()
    .toRequest(booksListing, Some(uri"http://localhost:8080"))
    .apply((BooksQuery("SF", 2016), 20, "xyz-abc-123"))
```

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): the Scala HTTP client you always wanted!
* sttp tapir: this project
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)
* [sttp shared](https://github.com/softwaremill/sttp-shared): shared web socket, FP abstractions, capabilities and streaming code.
* [sttp apispec](https://github.com/softwaremill/sttp-apispec): OpenAPI, AsyncAPI and JSON Schema models.

## Table of contents

```{eval-rst}
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart
   generate
   adopters
   support
   scala_2_3_platforms

.. toctree::
   :maxdepth: 2
   :caption: Tutorials
   
   tutorials/01_hello_world
   tutorials/02_openapi_docs
   tutorials/03_json
   tutorials/04_errors
   tutorials/05_multiple_inputs_outputs
   tutorials/06_error_variants
   tutorials/07_cats_effect

.. toctree::
   :maxdepth: 2
   :caption: How-to's
   
   examples
   external
   how-tos/delimited-path-parameters

.. toctree::
   :maxdepth: 2
   :caption: Endpoints

   endpoint/basics
   endpoint/ios
   endpoint/oneof
   endpoint/codecs
   endpoint/customtypes
   endpoint/schemas
   endpoint/enumerations
   endpoint/validation
   endpoint/contenttype
   endpoint/json
   endpoint/pickler
   endpoint/xml
   endpoint/forms
   endpoint/security
   endpoint/streaming
   endpoint/websockets
   endpoint/integrations
   endpoint/static

.. toctree::
   :maxdepth: 2
   :caption: Server interpreters

   server/overview
   server/akkahttp
   server/http4s
   server/zio-http4s
   server/netty
   server/nima
   server/finatra
   server/pekkohttp
   server/play
   server/vertx
   server/ziohttp
   server/armeria
   server/jdkhttp
   server/aws
   server/options
   server/path
   server/interceptors
   server/logic
   server/observability
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
   docs/json-schema

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

   other/stability
   other/other_interpreters
   other/mytapir
   other/grpc
   other/troubleshooting
   other/migrating
   other/goals
   other/contributing

