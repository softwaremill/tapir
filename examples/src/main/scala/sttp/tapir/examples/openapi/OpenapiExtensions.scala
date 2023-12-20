package sttp.tapir.examples.openapi

import io.circe.generic.auto.*
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.*
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.docs.apispec.DocsExtensionAttribute.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object OpenapiExtensions extends App {

  case class Sample(foo: Boolean, bar: String, baz: Int)

  case class MyExt(bar: String, baz: Int)

  val sampleEndpoint =
    endpoint.post
      .in("hello" / path[String]("world").docsExtension("x-path", 22))
      .in(query[String]("hi").docsExtension("x-query", 33))
      .in(jsonBody[Sample].docsExtension("x-request", MyExt("a", 1)))
      .out(jsonBody[Sample].example(Sample(false, "bar", 42)).docsExtension("x-response", "foo"))
      .errorOut(stringBody)
      .docsExtension("x-endpoint-level-string", "world")
      .docsExtension("x-endpoint-level-int", 11)
      .docsExtension("x-endpoint-obj", MyExt("42.42", 42))

  val rootExtensions = List(
    DocsExtension.of("x-root-bool", true),
    DocsExtension.of("x-root-obj", MyExt("string", 33))
  )

  val openapi = OpenAPIDocsInterpreter().toOpenAPI(sampleEndpoint, Info("title", "1.0"), rootExtensions)

  println(openapi.toYaml)
}
