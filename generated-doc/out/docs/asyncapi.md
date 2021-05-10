# Generating AsyncAPI documentation

To use, add the following dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-asyncapi-docs" % "0.18.0-M10"
"com.softwaremill.sttp.tapir" %% "tapir-asyncapi-circe-yaml" % "0.18.0-M10"
```

Tapir contains a case class-based model of the asyncapi data structures in the `asyncapi/asyncapi-model` subproject (the
model is independent from all other tapir modules and can be used stand-alone).
 
An endpoint can be converted to an instance of the model by using the `sttp.tapir.docs.asyncapi.AsyncAPIInterpreter` 
object:

```scala
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir._
import sttp.tapir.asyncapi.AsyncAPI
import sttp.tapir.docs.asyncapi.AsyncAPIInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

case class Response(msg: String, count: Int)
val echoWS = endpoint.out(
  webSocketBody[String, CodecFormat.TextPlain, Response, CodecFormat.Json](AkkaStreams))

val docs: AsyncAPI = AsyncAPIInterpreter.toAsyncAPI(echoWS, "Echo web socket", "1.0")
```

Such a model can then be refined, by adding details which are not auto-generated. Working with a deeply nested case 
class structure such as the `AsyncAPI` one can be made easier by using a lens library, e.g. [Quicklens](https://github.com/adamw/quicklens).

The documentation is generated in a large part basing on [schemas](endpoint/codecs.md#schemas). Schemas can be
[automatically derived and customised](endpoint/customtypes.md#schema-derivation).

Quite often, you'll need to define the servers, through which the API can be reached. Any servers provided to the 
`.toAsyncAPI` invocation will be supplemented with security requirements, as specified by the endpoints:

```scala
import sttp.tapir.asyncapi.Server

val docsWithServers: AsyncAPI = AsyncAPIInterpreter.toAsyncAPI(
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
import sttp.tapir.asyncapi.circe.yaml._

println(docs.toYaml)
```

## Options

Options can be customised by providing an implicit instance of `AsyncAPIDocsOptions`, when calling `.toAsyncAPI`.

* `subscribeOperationId`: basing on the endpoint's path and the entire endpoint, determines the id of the subscribe 
  operation. This can be later used by code generators as the name of the method to receive messages from the socket.
* `publishOperationId`: as above, but for publishing (sending messages to the web socket).
* `referenceEnums`: defines if enums should be converted to async api components and referenced later.
  This option can be applied to all enums in the schema, or only specific ones.
  `SObjectInfo` input parameter is a unique identifier of object in the schema.
  By default, it is fully qualified name of the class (when using `Validator.derivedEnum` or implicits from `sttp.tapir.codec.enumeratum._`).

## AsyncAPI Specification Extensions

AsyncAPI supports adding [extensions](https://www.asyncapi.com/docs/specifications/2.0.0#specificationExtensions)
as well as OpenAPI. There is `docsExtension` method available on parameters and endpoints. There are
`requestsDocsExtension` and `responsesDocsExtension` methods on `websocketBody`. Take a look at
**OpenAPI Specification Extensions** section of [documentation](../docs/openapi.md) to get a feeling on how to use it.

## Exposing AsyncAPI documentation

AsyncAPI documentation can be exposed through the [AsyncAPI playground](https://playground.asyncapi.io).