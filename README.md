# TAPIR, or Typed API descRiptions

Tapir allows you to describe your API endpoints as immutable Scala values. Each endpoint has a number of: input parameters, error-output parameters, and  normal-output parameters. Such an endpoint specification can then be translated to:

* a server, given the "business logic": a function, which computes output parameters based on input parameters. Currently supported: [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) routes/directives.
* a client, which is a function from the input parameters, to the output parameters. Currently supported: [sttp](https://github.com/softwaremill/sttp).
* documentation. Currently supported: [OpenAPI](https://www.openapis.org).

## Teaser

```scala
import tapir._

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
import io.circe.generic.auto._

val docs = booksListing.toOpenAPI("My Bookshop", "1.0")
println(docs.toYaml)

//

import tapir.server.akkahttp._
import akka.http.scaladsl.server.Route

val booksListingRoute: Route = booksListing.toRoute { (bfy: BooksFromYear, limit: Limit, at: AuthToken) =>
  Future.successful(List(Book("The Sorrows of Young Werther")))
}

//

import tapir.client.sttp._
import com.softwaremill.sttp._

val booksListingRequest: Request[Either[String, List[Book]], Nothing] = booksListing
  .toSttpRequest(uri"http://localhost:8080")
  .apply(BooksFromYear("SF", 2016), 20, "xyz-abc-123")
```

## Goals of the project

* programmer-friendly types (also in IntelliJ)
* as simple as possible to generate a server, client & docs
* first-class OpenAPI support. Provide as much or as little detail as needed.
* human-comprehensible types; types, that you are not afraid to explicitly write down
* reasonably type safe: only, and as much types to safely generate the server/client/docs
* discoverable API through standard auto-complete
* separate "business logic" from endpoint definition & documentation

## Similar projects

There's a number of similar projects from which Tapir draws inspiration:

* [endpoints](https://github.com/julienrf/endpoints)
* [typedapi](https://github.com/pheymann/typedapi~)
* [rho](https://github.com/http4s/rho)
* [typed-schema](https://github.com/TinkoffCreditSystems/typed-schema)

## What's missing

* streaming support
* multi-part bodies
* file support, non-string-based bodies, form-fields bodies
* coproducts/sealed trait families/discriminators in object hierarchies
* support for OpenAPI's formats (base64, binary, email, ...)
* ... and much more :)