# tapir, or Typed API descRiptions

[![Join the chat at https://gitter.im/softwaremill/tapir](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/softwaremill/tapir?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/softwaremill/tapir.svg?branch=master)](https://travis-ci.org/softwaremill/tapir)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.tapir/tapir-core_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.tapir/tapir-core_2.12)

With tapir you can describe HTTP API endpoints as immutable Scala values. Each endpoint can contain a number of 
input parameters, error-output parameters, and normal-output parameters. An endpoint specification can be 
interpreted as:

* a server, given the "business logic": a function, which computes output parameters based on input parameters. 
  Currently supported: 
  * [Akka HTTP](https://tapir-scala.readthedocs.io/en/latest/server/akkahttp.html) `Route`s/`Directive`s.
  * [Http4s](https://tapir-scala.readthedocs.io/en/latest/server/http4s.html) `HttpRoutes[F]`
* a client, which is a function from input parameters to output parameters. Currently supported: [sttp](https://tapir-scala.readthedocs.io/en/latest/sttp.html).
* documentation. Currently supported: [OpenAPI](https://tapir-scala.readthedocs.io/en/latest/openapi.html).

## Teaser

```scala
import tapir._
import tapir.json.circe._
import io.circe.generic.auto._

type Limit = Int
type AuthToken = String
case class BooksFromYear(genre: String, year: Int)
case class Book(title: String)

val booksListing: Endpoint[(BooksFromYear, Limit, AuthToken), String, List[Book], Nothing] = endpoint
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

## Documentation

tapir documentation is available at [tapir-scala.readthedocs.io](http://tapir-scala.readthedocs.io).

## Quickstart with sbt

Add the following dependency:

```scala
"com.softwaremill.tapir" %% "tapir-core" % "0.4"
```

You'll need partial unification enabled in the compiler (alternatively, you'll need to manually provide type arguments in some cases):

```scala
scalacOptions += "-Ypartial-unification"
```

Then, import:

```scala
import tapir._
```

And finally, type `endpoint.` and see where auto-complete gets you!

---

Sidenote for scala 2.12.4 and higher: if you encounter an issue with compiling your project because of 
a `StackOverflowException` related to [this](https://github.com/scala/bug/issues/10604) scala bug, 
please increase your stack memory. Example:

```
sbt -J-Xss4M clean compile
```

## Contributing

Tapir is an early stage project. Everything might change. All suggestions welcome :)

See the list of [issues](https://github.com/softwaremill/tapir/issues) and pick one! Or report your own.

If you are having doubts on the *why* or *how* something works, don't hesitate to ask a question on
[gitter](https://gitter.im/softwaremill/tapir) or via github. This probably means that the documentation, scaladocs or 
code is unclear and be improved for the benefit of all.