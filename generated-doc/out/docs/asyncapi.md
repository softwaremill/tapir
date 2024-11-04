# Generating AsyncAPI documentation

To use, add the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-asyncapi-docs" % "1.11.8"
"com.softwaremill.sttp.apispec" %% "asyncapi-circe-yaml" % "..." // see https://github.com/softwaremill/sttp-apispec
```

Tapir contains a case class-based model of the asyncapi data structures in the `asyncapi/asyncapi-model` subproject (the
model is independent from all other tapir modules and can be used stand-alone).
 
An endpoint can be converted to an instance of the model by using the `sttp.tapir.docs.asyncapi.AsyncAPIInterpreter` 
object:

```scala
import sttp.apispec.asyncapi.AsyncAPI
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.*
import sttp.tapir.docs.asyncapi.AsyncAPIInterpreter
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

case class Response(msg: String, count: Int)
val echoWS = endpoint.out(
  webSocketBody[String, CodecFormat.TextPlain, Response, CodecFormat.Json](PekkoStreams))

val docs: AsyncAPI = AsyncAPIInterpreter().toAsyncAPI(echoWS, "Echo web socket", "1.0")
```

Such a model can then be refined, by adding details which are not auto-generated. Working with a deeply nested case 
class structure such as the `AsyncAPI` one can be made easier by using a lens library, e.g. [Quicklens](https://github.com/adamw/quicklens).

The documentation is generated in a large part basing on [schemas](../endpoint/codecs.html#schemas). Schemas can be
[automatically derived and customised](../endpoint/schemas.md).

Quite often, you'll need to define the servers, through which the API can be reached. Any servers provided to the 
`.toAsyncAPI` invocation will be supplemented with security requirements, as specified by the endpoints:

```scala
import sttp.apispec.asyncapi.Server

val docsWithServers: AsyncAPI = AsyncAPIInterpreter().toAsyncAPI(
  echoWS, 
  "Echo web socket", 
  "1.0",
  List("production" -> Server("api.example.com", "wss"))
)
```

Servers can also be later added through methods on the `AsyncAPI` object.

Multiple endpoints can be converted to an `AsyncAPI` instance by calling the method using a list of endpoints.

The asyncapi case classes can then be serialised, either to JSON or YAML using [Circe](https://circe.github.io/circe/):

```scala
import sttp.apispec.asyncapi.circe.yaml.*

println(docs.toYaml)
```

## Options

Options can be customised by providing an instance of `AsyncAPIDocsOptions` to the interpreter:

* `subscribeOperationId`: basing on the endpoint's path and the entire endpoint, determines the id of the subscribe 
  operation. This can be later used by code generators as the name of the method to receive messages from the socket.
* `publishOperationId`: as above, but for publishing (sending messages to the web socket).

## Inlined and referenced schemas

All named schemas (that is, schemas which have the `Schema.name` property defined) will be referenced at point of
use, and their definitions will be part of the `components` section. If you'd like a schema to be inlined, instead
of referenced, [modify the schema](../endpoint/schemas.md) removing the name.

## AsyncAPI Specification Extensions

AsyncAPI supports adding [extensions](https://www.asyncapi.com/docs/specifications/2.0.0#specificationExtensions)
similarly as in OpenAPI. 

Specification extensions can be added by first importing an extension method, and then calling the `docsExtension`
method which manipulates the appropriate attribute on the schema, endpoint or endpoint input/output:

```scala
import sttp.tapir.docs.apispec.DocsExtensionAttribute.*

endpoint
  .post
  .in(query[String]("hi").docsExtension("x-query", 33))
  .docsExtension("x-endpoint-level-string", "world")
```

There are `requestsDocsExtension` and `responsesDocsExtension` methods to add extensions to a `websocketBody`. Take a 
look at **OpenAPI Specification Extensions** section of [documentation](../docs/openapi.md) to get a feeling on how to use it.

## Exposing AsyncAPI documentation

AsyncAPI documentation can be exposed through the [AsyncAPI playground](https://playground.asyncapi.io).
