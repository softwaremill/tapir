# tapir

<div style="text-align: center">
<p>Declarative, type-safe web endpoints library.</p>
<img src="https://github.com/softwaremill/tapir/raw/master/doc/logo.png" alt="tapir" height="100" width="100" />
</div>

## Intro

With tapir, you can describe HTTP API endpoints as immutable Scala values. Each endpoint can contain a number of
input and output parameters. An endpoint specification can be interpreted as:

* a server, given the "business logic": a function, which computes output parameters based on input parameters.
  Currently supported:
  * [Akka HTTP](server/akkahttp.md) `Route`s/`Directive`s
  * [Http4s](server/http4s.md) `HttpRoutes[F]` (using cats-effect or [ZIO](server/zio-http4s.md))
  * [Netty](server/netty.md) (using `Future`s, cats-effect or ZIO)
  * [Helidon Níma](server/nima.md) (using JVM 21 Virtual Threads and direct style)
  * [Finatra](server/finatra.md) `http.Controller`
  * [Pekko HTTP](server/pekkohttp.md) `Route`s/`Directive`s
  * [Play](server/play.md) `Route`
  * [Vert.X](server/vertx.md) `Router => Route` (using `Future`s, cats-effect or ZIO)
  * [ZIO Http](server/ziohttp.md) `Http`
  * [Armeria](server/armeria.md) `HttpServiceWithRoutes` (using `Future`s, cats-effect or ZIO)
  * [JDK HTTP](server/jdkhttp.md) `HttpHandler` (simple, synchronous API only)
  * [aws](server/aws.md) through Lambda/SAM/Terraform
  * [gRPC](grpc.md)
* a client, which is a function from input parameters to output parameters.
  Currently supported:
  * [sttp](client/sttp.md)
  * [Play](client/play.md)
  * [Http4s](client/http4s.md)
* documentation. Currently supported:
  * [OpenAPI](docs/openapi.md)
  * [AsyncAPI](docs/asyncapi.md)
  * [Json Schema](docs/json-schema.md)

Depending on how you prefer to explore the library, take a look at one of the [examples](examples.md) or read on
for a more detailed description of how tapir works!

## Why tapir?

* **type-safety**: compile-time guarantees, develop-time completions, read-time information
* **declarative**: separate the shape of the endpoint (the "what"), from the server logic (the "how")
* **OpenAPI / Swagger integration**: generate documentation from endpoint descriptions
* **observability**: leverage the metadata to report rich metrics and tracing information
* **abstraction**: re-use common endpoint definitions, as well as individual inputs/outputs
* **library, not a framework**: integrates with your stack

## Adopt a tapir

```eval_rst
.. raw:: html

   <iframe
     frameborder=0
     sandbox="allow-scripts allow-same-origin allow-forms allow-downloads allow-popups"
     src="https://adopt-tapir.softwaremill.com/embedded-form"
     width="100%"
     height="603"
   ></iframe>
```

## Availability

Tapir is available:

* all modules - Scala 2.12 and 2.13 on the JVM (Java 11+)
* selected modules - Scala 3 on the JVM (Java 11+)
* selected modules - Scala 2.12, 2.13 and 3 using Scala.JS
* selected modules - Scala 2.12, 2.13 and 3 using Scala Native

Tapir is licensed under Apache2, the source code is [available on GitHub](https://github.com/softwaremill/tapir).

## Adopters

Is your company already using tapir? We're continually expanding the "adopters" section in the documentation; the more the merrier! It would be great to feature your company's logo, but in order to do that, we'll need written permission to avoid any legal misunderstandings.

Please email us at [tapir@softwaremill.com](mailto:tapir@softwaremill.com) from your company's email with a link to your logo (if we can use it, of course!) or with details who to kindly ask for permission to feature the logo in tapir's documentation. We'll handle the rest.

Thank you!

<div style="display: flex; justify-content: space-between; align-items: center;">
<a href="https://www.adobe.com" title="Adobe"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/adobe.png" alt="Adobe" width="160"/></a>
<a href="https://swisscom.com" title="Swisscom"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/swisscom.svg" alt="Swisscom" width="160"/></a>
<a href="https://swissborg.com" title="Swissborg"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/swissborg.png" alt="Swissborg" width="160"/></a>
</div>
<div style="display: flex; justify-content: space-between; align-items: center;">
<a href="https://kaizo.com" title="Kaizo"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/kaizo.png" alt="Kaizo" width="160"/></a>
<a href="https://www.process.st/" title="Process Street"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/process_street.png" alt="Process Street" width="100"/></a>
<a href="https://tranzzo.com" title="Tranzzo"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/tranzzo.svg" alt="Tranzzo" width="160"/></a>
</div>
<div style="display: flex; justify-content: space-between; align-items: center; margin-top:10px;">
<a href="https://www.kelkoogroup.com" title="Kelkoo group"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/kelkoogroup.png" alt="Kelkoo group" width="160"/></a>
<a href="https://www.softwaremill.com/" title="SoftwareMill"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/softwaremill.png" alt="SoftwareMill" width="160"/></a>
<a href="https://www.carvana.com" title="Carvana"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/carvana.svg" alt="Carvana" width="160"/></a>
</div>
<div style="display: flex; justify-content: space-between; align-items: center;">
<a href="https://www.moneyfarm.com" title="Moneyfarm"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/moneyfarm.png" alt="Moneyfarm" width="160"/></a>
<a href="https://www.ocadogroup.com/about-us/ocado-technology/" title="Ocado Technology"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/ocado.png" alt="Ocado" width="160"/></a>
<a href="https://www.wegtam.com" title="Wegtam"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/wegtam.svg" alt="Wegtam" width="160"/></a>
</div>
<div style="display: flex; justify-content: space-between; align-items: center; height: 100px;">
<a href="http://www.broad.app/" title="Broad"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/broad.png" alt="Broad" width="160"/></a>
<a href="https://www.kensu.io?utm_source=github&utm_campaign=tapir"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/kensu.png" alt="Kensu" width="160"/></a>
<a href="https://www.colisweb.com" title="Colisweb"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/colisweb.png" alt="Colisweb" width="160"/></a>
</div>
<div style="display: flex; justify-content: space-between; align-items: center; height: 100px;">
<a href="http://www.iceo.co/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/iceo.png" alt="iceo" width="160"/></a>
<a href="http://www.dpgrecruitment.nl/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/dpg-recruitment.svg" alt="dpg" width="160"/></a>
<a href="https://www.hunters.security/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/hunters.png" alt="hunters" width="160"/></a>
</div>
<div style="display: flex; justify-content: space-between; align-items: center; height: 100px;">
<div></div>
<a href="https://www.moia.io/en"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/moia.png" alt="moia" width="160"/></a>
<div></div>
</div>

## Code teaser

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

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

import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

val docs = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0")
println(docs.toYaml)


// Convert to akka-http Route

import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import akka.http.scaladsl.server.Route
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

def bookListingLogic(bfy: BooksQuery,
                     limit: Limit,
                     at: AuthToken): Future[Either[String, List[Book]]] =
  Future.successful(Right(List(Book("The Sorrows of Young Werther"))))

val booksListingRoute: Route = AkkaHttpServerInterpreter()
  .toRoute(booksListing.serverLogic((bookListingLogic _).tupled))


// Convert to sttp Request

import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.client3._

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

## Sponsors

Development and maintenance of sttp tapir is sponsored by [SoftwareMill](https://softwaremill.com), a software development and consulting company. We help clients scale their business through software. Our areas of expertise include backends, distributed systems, blockchain, machine learning and data analytics.

[![](https://files.softwaremill.com/logo/logo.png "SoftwareMill")](https://softwaremill.com)

## Commercial Support

We offer commercial support for sttp and related technologies, as well as development services. [Contact us](https://softwaremill.com/contact/) to learn more about our offer!

## Table of contents

```eval_rst
.. toctree::
   :maxdepth: 2
   :caption: Getting started

   quickstart
   examples
   stability

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

   other_interpreters
   mytapir
   grpc
   troubleshooting
   migrating
   goals
   contributing

