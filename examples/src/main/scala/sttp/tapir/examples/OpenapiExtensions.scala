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
    Extension.of("x-root-bool", true),
    Extension.of("x-root-obj", MyExt("string", 33))
  )

  val openapi = OpenAPIDocsInterpreter.toOpenAPI(sampleEndpoint, Info("title", "1.0"), rootExtensions)

  println(openapi.toYaml)

  /* Result:

      openapi: 3.0.3
      info:
        title: title
        version: '1.0'
      paths:
        /hello/{world}:
          post:
            operationId: postHelloWorld
            parameters:
            - name: world
              in: path
              required: true
              schema:
                type: string
              x-path: 22
            - name: hi
              in: query
              required: true
              schema:
                type: string
              x-query: 33
            requestBody:
              content:
                application/json:
                  schema:
                    $ref: '#/components/schemas/Sample'
              required: true
              x-request:
                bar: a
                baz: 1
            responses:
              '200':
                description: ''
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/Sample'
                    example:
                      foo: false
                      bar: bar
                      baz: 42
                x-response: foo
              default:
                description: ''
                content:
                  text/plain:
                    schema:
                      type: string
            x-endpoint-level-string: world
            x-endpoint-level-int: 11
            x-endpoint-obj:
              bar: '42.42'
              baz: 42
      components:
        schemas:
          Sample:
            required:
            - foo
            - bar
            - baz
            type: object
            properties:
              foo:
                type: boolean
              bar:
                type: string
              baz:
                type: integer
      x-root-bool: true
      x-root-obj:
        bar: string
        baz: 33

   */
}
