![tapir](https://github.com/softwaremill/tapir/raw/master/banner.png)

# Welcome!

[![Ideas, suggestions, problems, questions](https://img.shields.io/badge/Discourse-ask%20question-blue)](https://softwaremill.community/c/tapir)
[![CI](https://github.com/softwaremill/tapir/workflows/CI/badge.svg)](https://github.com/softwaremill/tapir/actions?query=workflow%3A%22CI%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp.tapir/tapir-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.sttp.tapir/tapir-core_2.13)

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
* documentation can be generated in the [OpenAPI](https://tapir.softwaremill.com/en/latest/docs/openapi.html), [AsyncAPI](https://tapir.softwaremill.com/en/latest/docs/asyncapi.html) and [JSON Schema](https://tapir.softwaremill.com/en/latest/docs/json-schema.html) formats

Depending on how you'd prefer to explore Tapir, this documentation has three main sections:

1. There's a number of [tutorials](https://tapir.softwaremill.com/en/latest/tutorials/01_hello_world.html), which provide a gentle introduction to the library
2. Nothing compares to tinkering with working code, that's why we've prepared [runnable examples](https://tapir.softwaremill.com/en/latest/examples.html),
   covering solutions to many "everyday" problems
3. Finally, the reference documentation describes all of Tapir's aspects in depth - take a look at the menu on
   the left, starting with the "Endpoints" section

## Documentation

Tapir documentation is available at [tapir.softwaremill.com](http://tapir.softwaremill.com).

## Why tapir?

* **type-safety**: compile-time guarantees, develop-time completions, read-time information
* **declarative**: separate the shape of the endpoint (the "what"), from the server logic (the "how")
* **OpenAPI / Swagger integration**: generate documentation from endpoint descriptions
* **observability**: leverage the metadata to report rich metrics and tracing information
* **abstraction**: re-use common endpoint definitions, as well as individual inputs/outputs
* **library, not a framework**: integrates with your stack

## Adopters

Is your company already using tapir? We're continually expanding the "adopters" section in the documentation; the more the merrier! It would be great to feature your company's logo, but in order to do that, we'll need written permission to avoid any legal misunderstandings.

Please email us at [tapir@softwaremill.com](mailto:tapir@softwaremill.com) from your company's email with a link to your logo (if we can use it, of course!) or with details who to kindly ask for permission to feature the logo in tapir's documentation. We'll handle the rest.

||||
| :---: | :---: | :---: |       
| <a href="https://www.adobe.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/adobe.png" alt="Adobe" width="160"/></a> | <a href="https://swisscom.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/swisscom.svg" alt="Swisscom" width="160"/></a> | <a href="https://swissborg.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/swissborg.png" alt="Swissborg" width="160"/></a> |
| <a href="https://kaizo.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/kaizo.png" alt="Kaizo" width="160"/></a> | <a href="https://www.process.st/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/process_street.png" alt="Process Street" width="100"/></a> | <a href="https://www.tranzzo.com/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/tranzzo.svg" alt="Tranzzo" width="160"/></a> |
| <a href="https://www.kelkoogroup.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/kelkoogroup.png" alt="Kelkoo group" width="160"/></a> | <a href="https://www.softwaremill.com/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/softwaremill.png" alt="SoftwareMill" width="160"/></a> | <a href="https://www.carvana.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/carvana.svg" alt="Carvana" width="160"/></a> |
| <a href="https://www.moneyfarm.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/moneyfarm.png" alt="Moneyfarm" width="160"/></a> | <a href="https://www.ocadogroup.com/about-us/ocado-technology"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/ocado.png" alt="Ocado Technology" width="160"/></a> | <a href="https://www.wegtam.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/wegtam.svg" alt="Wegtam" width="160"/></a> |
| <a href="https://www.broad.app"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/broad.png" alt="Broad" width="160"/></a> | <a href="https://www.kensu.io?utm_source=github&utm_campaign=tapir"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/kensu.png" alt="Kensu" width="160"/></a> | <a href="https://www.colisweb.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/colisweb.png" alt="Colisweb" width="160"/></a> |
| <a href="http://www.iceo.co/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/iceo.png" alt="iceo" width="160"/></a> | <a href="http://www.dpgrecruitment.nl/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/dpg-recruitment.svg" alt="dpg" width="160"/></a> | <a href="https://www.hunters.security/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/hunters.png" alt="hunters" width="160"/></a> | 
| <a href="https://www.moia.io/en"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/moia.png" alt="moia" width="160"/></a> | <a href="https://www.pitsdatarecovery.net"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/pits.svg" alt="pits" width="100"/></a> | <a href="https://www.hootsuite.com"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/hootsuite.png" alt="hootsuite" width="160"/></a> |
| <a href="https://www.ematiq.com/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/ematiq.png" alt="ematiq" width="100"/></a> | <a href="https://www.fugo.ai/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/fugo.png" alt="fugo" width="100"/></a> | <a href="https://budgetbakers.com/"><img src="https://github.com/softwaremill/tapir/raw/master/doc/adopters/budgetbakers.svg" alt="budgetbakers" width="100"/></a> |

## Teaser

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

## Quickstart with sbt

Add the following dependency:

```sbt
"com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.8"
```

Then, import:

```scala
import sttp.tapir._
```

And finally, type `endpoint.` and see where auto-complete gets you!

### Scala 2.12

Partial unification is now enabled by default from Scala 2.13. However, if you're using Scala 2.12 or older, then
you'll need partial unification enabled in the compiler (alternatively, you'll need to manually provide type
arguments in some cases):

```sbt
scalacOptions += "-Ypartial-unification"
```

Sidenote for scala 2.12.4 and higher: if you encounter an issue with compiling your project because of 
a `StackOverflowException` related to [this](https://github.com/scala/bug/issues/10604) scala bug, 
please increase your stack memory. Example:

```shell
sbt -J-Xss4M clean compile
```

## Other sttp projects

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): the Scala HTTP client you always wanted!
* sttp tapir: this project
* [sttp model](https://github.com/softwaremill/sttp-model): simple HTTP model classes (used by client & tapir)
* [sttp shared](https://github.com/softwaremill/sttp-shared): shared web socket, FP abstractions, capabilities and streaming code.
* [sttp apispec](https://github.com/softwaremill/sttp-apispec): OpenAPI, AsyncAPI and JSON Schema models.

## Contributing

All suggestions welcome :)

See the list of [issues](https://github.com/softwaremill/tapir/issues) and pick one! Or report your own.

If you are having doubts on the _why_ or _how_ something works, don't hesitate to ask a question on
[discourse](https://softwaremill.community/c/tapir) or via github. This probably means that the documentation, scaladocs or
code is unclear and be improved for the benefit of all.
In order to develop the documentation, you can use the `doc/watch.sh` script, which runs Sphinx using Python.
Use `doc/requirements.txt` to set up your Python environment with `pip`. If you're using Nix, you can just run `nix develop`
in the `doc` directory to set up a working shell with all the dependencies.

The `core` module needs to remain binary-compatible with earlier versions. To check if your changes meet this requirement,
you can run `core/mimaReportBinaryIssues` from the sbt console.
However, be aware that tags from the repository aren’t automatically fetched during forking; hence, the command will not operate without first fetching the tags.

After forking and cloning the repository, add the original repository as a remote:

```
git remote add upstream git@github.com:softwaremill/tapir.git
```

Fetch the tags from the upstream:
```
git fetch --tags upstream
```

When you have a PR ready, take a look at our ["How to prepare a good PR" guide](https://softwaremill.community/t/how-to-prepare-a-good-pr-to-a-library/448). Thanks! :)

## Scoping which projects are included by `sbt`

* when `STTP_NATIVE` is set, Scala native projects are included in the build (when running `sbt`)
* when `ALSO_LOOM` is set, projects using virtual threads and requiring Java 21 are included in the build 
* when `ONLY_LOOM` is set, only projects using virtual threads are included in the build

### Testing locally

The JS tests use [Gecko instead of Chrome](https://github.com/scala-js/scala-js-env-selenium/issues/119), although this
causes another problem: out of memory when running JS tests for multiple modules. Work-arounds:

- run only tests for a specific Scala version and platform using `testScoped 2.13 JS` (supported versions: 2.12, 2.13, 3; supported platforms: JVM, JS, Native)
- test single JS projects
- use CI (GitHub Actions) to test all projects - the `.github/workflows/ci.yml` enumerates them one by one

You can test only server/client/doc/other projects using `testServers`, `testClients`, `testDocs` and `testOther`.

To verify that the code snippet in docs compile, run `compileDocumentation`. A full mdoc run is done during a release
(when the documentation is generated).

### Importing into IntelliJ

By default, when importing to IntelliJ, only the Scala 3/JVM subprojects will be imported. This is controlled by the `ideSkipProject` setting in `build.sbt` (inside `commonSettings`).

If you'd like to work on a different platform or Scala version, simply change this setting temporarily so that the correct subprojects are imported. For example:

```
// import only Scala 2.13, JS projects
ideSkipProject := (scalaVersion.value != scala2_13) || !thisProjectRef.value.project.contains("JS")

// import only Scala 3, JVM projects
ideSkipProject := (scalaVersion.value != scala3) || thisProjectRef.value.project.contains("JS") || thisProjectRef.value.project.contains("Native"),

// import only Scala 2.13, Native projects
ideSkipProject := (scalaVersion.value != scala2_13) || !thisProjectRef.value.project.contains("Native")
```

## Commercial Support

We offer commercial support for tapir and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

## Copyright

Copyright (C) 2018-2024 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
