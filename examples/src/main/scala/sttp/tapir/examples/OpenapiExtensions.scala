package sttp.tapir.examples

import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._

object OpenapiExtensions extends App {

  case class Sample(foo: Boolean, bar: String, baz: Int)

  case class MyExt(bar: String, baz: Int)

  val sampleEndpoint =
    endpoint.post
      .in("hello" / path[String]("world").extension("x-path", 22))
      .in(query[String]("hi").extension("x-query", 33))
      .in(jsonBody[Sample].extension("x-request", MyExt("a", 1)))
      .out(jsonBody[Sample].example(Sample(false, "bar", 42)).extension("x-response", "foo"))
      .errorOut(stringBody)
      .extension("x-endpoint-level-string", "world")
      .extension("x-endpoint-level-int", 11)
      .extension("x-endpoint-obj", MyExt("42.42", 42))

  val rootExtensions = List(
    DocsExtension.of("x-root-bool", true),
    DocsExtension.of("x-root-obj", MyExt("string", 33))
  )

  val openapi = OpenAPIDocsInterpreter.toOpenAPI(sampleEndpoint, Info("title", "1.0"), rootExtensions)

  println(openapi.toYaml)
}
