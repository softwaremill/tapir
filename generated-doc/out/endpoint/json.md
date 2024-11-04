# Working with JSON

Json values are supported through codecs, which encode/decode values to json strings. Most often, you'll be using a
third-party library to perform the actual json parsing/printing. See below for the list of supported libraries.

All the integrations, when imported into scope, define `jsonBody[T]` and `jsonQuery[T]` methods.

Instead of providing the json codec as an implicit value, this method depends on library-specific implicits being in
scope, and basing on these values creates a json codec. The derivation also requires
an implicit `Schema[T]` instance, which can be automatically derived. For more details see sections on
[schema derivation](schemas.md) and on supporting [custom types](customtypes.md) in general. Such a design provides
better error reporting, in case one of the components required to create the json codec is missing.

```{note}
Note that the process of deriving schemas, and deriving library-specific json encoders and decoders is entirely
separate (unless you're using the pickler module - see below). The first is controlled by tapir, the second - by the
json library. Any customisation, e.g. for field naming or inheritance strategies, must be done separately for both
derivations.
```

## Pickler

Alternatively, instead of deriving schemas and library-specific json encoders and decoders separately, you can use
the experimental [pickler](pickler.md) module, which takes care of both derivation in a consistent way, which allows
customization with a single, common configuration API.

## Implicit json codecs

If you have a custom, implicit `Codec[String, T, Json]` instance, you should use the `customCodecJsonBody[T]` method instead.
This description of endpoint input/output, instead of deriving a codec basing on other library-specific implicits, uses
the json codec that is in scope.

## JSON as string

If you'd like to work with JSON bodies in a serialised `String` form, instead of integrating on a higher level using
one of the libraries mentioned below, you should use the `stringJsonBody` input/output. Note that in this case, the
serialising/deserialising of the body must be part of the [server logic](../server/logic.md).

A schema can be provided in this case as well:

```scala
import sttp.tapir.*
import sttp.tapir.generic.auto.*
case class MyBody(field: Int)
stringJsonBody.schema(implicitly[Schema[MyBody]].as[String])
```

## Circe

To use [Circe](https://github.com/circe/circe), add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.8"
```

Next, import the package (or extend the `TapirJsonCirce` trait, see [MyTapir](../other/mytapir.md)):

```scala
import sttp.tapir.json.circe.*
```

The above import brings into scope the `jsonBody[T]` body input/output description, which creates a codec, given an
in-scope circe `Encoder`/`Decoder` and a `Schema`. Circe includes a couple of approaches to generating encoders/decoders
(manual, semi-auto and auto), so you may choose whatever suits you.

Note that when using Circe's auto derivation, any encoders/decoders for custom types must be in scope as well.

For example, to automatically generate a JSON codec for a case class:

```scala
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*

case class Book(author: String, title: String, year: Int)

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

### Configuring the circe printer

Circe lets you select an instance of `io.circe.Printer` to configure the way JSON objects are rendered. By default
Tapir uses `Printer.nospaces`, which would render:

```scala
import io.circe.*

Json.obj(
  "key1" -> Json.fromString("present"),
  "key2" -> Json.Null
)
```

as

```json
{"key1":"present","key2":null}
```

Suppose we would instead want to omit `null`-values from the object and pretty-print it. You can configure this by
overriding the `jsonPrinter` in `tapir.circe.json.TapirJsonCirce`:

```scala
import sttp.tapir.json.circe.*
import io.circe.Printer

object MyTapirJsonCirce extends TapirJsonCirce:
  override def jsonPrinter: Printer = Printer.spaces2.copy(dropNullValues = true)

import MyTapirJsonCirce.*
```

Now the above JSON object will render as

```json
{"key1":"present"}
```

## µPickle

To use [µPickle](http://www.lihaoyi.com/upickle/) add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-upickle" % "1.11.8"
```

Next, import the package (or extend the `TapirJsonuPickle` trait, see [MyTapir](../other/mytapir.md) and add `TapirJsonuPickle` not `TapirCirceJson`):

```scala
import sttp.tapir.json.upickle.*
```

µPickle requires a `ReadWriter` in scope for each type you want to serialize. In order to provide one use the `macroRW` macro in the companion object as follows:

```scala
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import upickle.default.*
import sttp.tapir.json.upickle.*

case class Book(author: String, title: String, year: Int)

object Book:
  given ReadWriter[Book] = macroRW

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

Like Circe, µPickle allows you to control the rendered json output. Please see the [Custom Configuration](https://com-lihaoyi.github.io/upickle/#CustomConfiguration) of the manual for details.

For more examples, including making a custom encoder/decoder, see [TapirJsonuPickleTests.scala](https://github.com/softwaremill/tapir/blob/master/json/upickle/src/test/scala/sttp/tapir/json/upickle/TapirJsonuPickleTests.scala)

## Play JSON

To use [Play JSON](https://github.com/playframework/play-json) for **Play 3.0**, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-play" % "1.11.8"
```

For **Play 2.9** use:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-play29" % "1.11.8"
```

Next, import the package (or extend the `TapirJsonPlay` trait, see [MyTapir](../other/mytapir.md) and add `TapirJsonPlay` not `TapirCirceJson`):

```scala
import sttp.tapir.json.play.*
```

Play JSON requires `Reads` and `Writes` implicit values in scope for each type you want to serialize.

## Spray JSON

To use [Spray JSON](https://github.com/spray/spray-json) add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-spray" % "1.11.8"
```

Next, import the package (or extend the `TapirJsonSpray` trait, see [MyTapir](../other/mytapir.md) and add `TapirJsonSpray` not `TapirCirceJson`):

```scala
import sttp.tapir.json.spray.*
```

Spray JSON requires a `JsonFormat` implicit value in scope for each type you want to serialize.

## Tethys JSON

To use [Tethys JSON](https://github.com/tethys-json/tethys) add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-tethys" % "1.11.8"
```

Next, import the package (or extend the `TapirJsonTethys` trait, see [MyTapir](../other/mytapir.md) and add `TapirJsonTethys` not `TapirCirceJson`):

```scala
import sttp.tapir.json.tethysjson.*
```

Tethys JSON requires `JsonReader` and `JsonWriter` implicit values in scope for each type you want to serialize.

## Jsoniter Scala

To use [Jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % "1.11.8"
```

Next, import the package (or extend the `TapirJsonJsoniter` trait, see [MyTapir](../other/mytapir.md) and add `TapirJsonJsoniter` not `TapirCirceJson`):

```scala
import sttp.tapir.json.jsoniter.*
```

Jsoniter Scala requires `JsonValueCodec` implicit value in scope for each type you want to serialize.

## Json4s

To use [json4s](https://github.com/json4s/json4s) add the following dependencies to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-json4s" % "1.11.8"
```

And one of the implementations:

```scala
"org.json4s" %% "json4s-native" % "4.0.7"
// Or
"org.json4s" %% "json4s-jackson" % "4.0.7"
```

Next, import the package (or extend the `TapirJson4s` trait, see [MyTapir](../other/mytapir.md) and add `TapirJson4s` instead of `TapirCirceJson`):

```scala
import sttp.tapir.json.json4s.*
```

Json4s requires `Serialization` and `Formats` implicit values in scope, for example:

```scala
import org.json4s.*
// ...
given Serialization = org.json4s.jackson.Serialization
given Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)
```

## Zio JSON

To use [zio-json](https://github.com/zio/zio-json), add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.11.8"
```
Next, import the package (or extend the `TapirJsonZio` trait, see [MyTapir](../other/mytapir.md) and add `TapirJsonZio` instead of `TapirCirceJson`):

```scala
import sttp.tapir.json.zio.*
```

Zio JSON requires `JsonEncoder` and `JsonDecoder` implicit values in scope for each type you want to serialize.

## JSON query parameters

You can specify query parameters in JSON format by using the `jsonQuery` method. For example, using Circe:

```scala
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*

case class Book(author: String, title: String, year: Int)

val bookQuery: EndpointInput.Query[Book] = jsonQuery[Book]("book")
```

## Other JSON libraries

To add support for additional JSON libraries, see the
[sources](https://github.com/softwaremill/tapir/blob/master/json/circe/src/main/scala/sttp/tapir/json/circe/TapirJsonCirce.scala)
for the Circe codec (which is just a couple of lines of code).

## Coproducts (enums, sealed traits, classes)

If you are serialising a sealed hierarchy, such as a Scala 3 `enum`, a `sealed trait` or `sealed class`, the configuration
of [schema derivation](schemas.md) will have to match the configuration of your json library. Different json libraries
have different defaults when it comes to a discrimination strategy, so in order to have the schemas (and hence the
documentation) in sync with how the values are serialised, you will have to configure schema derivation as well.

Schemas are referenced at the point of `jsonBody` and `jsonQuery` usage, so any configuration must be available in the implicit scope
when these methods are called.

## Optional json bodies

When the body is specified as an option, e.g. `jsonBody[Option[Book]]`, an empty body will be decoded as `None`.

This is implemented by passing `null` to the json-library-specific decoder, when the schema specifies that the value is
optional, and the body is empty.

## Next

Read on about [working with forms](forms.md).
