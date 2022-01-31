# Working with JSON

Json values are supported through codecs, which encode/decode values to json strings. Most often, you'll be using a
third-party library to perform the actual json parsing/printing. Currently, [zio-json](https://github.com/zio/zio-json), [Circe](https://github.com/circe/circe), [µPickle](http://www.lihaoyi.com/upickle/), [Spray JSON](https://github.com/spray/spray-json), [Play JSON](https://github.com/playframework/play-json), [Tethys JSON](https://github.com/tethys-json/tethys), [Jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala), and [Json4s](https://github.com/json4s/json4s) are supported.

All of the integrations, when imported into scope, define a `jsonBody[T]` method. This method depends on 
library-specific implicits being in scope, and derives from them a json codec. The derivation also requires implicit
`Schema[T]` and `Validator[T]` instances, which should be automatically derived. For more details see documentation 
on supporting [custom types](customtypes.md).

If you have a custom, implicit `Codec[String, T, Json]` instance, you should use the `customJsonBody[T]` method instead. 
This description of endpoint input/output, instead of deriving a codec basing on other library-specific implicits, uses 
the json codec that is in scope.

## Circe

To use Circe, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "0.20.0-M8"
```

Next, import the package (or extend the `TapirJsonCirce` trait, see [MyTapir](../mytapir.md)):

```scala
import sttp.tapir.json.circe._
```

This will allow automatically deriving `Codec`s which, given an in-scope circe `Encoder`/`Decoder` and a `Schema`, 
will create a codec using the json media type. Circe includes a couple of approaches to generating encoders/decoders 
(manual, semi-auto and auto), so you may choose whatever suits you.

Note that when using Circe's auto derivation, any encoders/decoders for custom types must be in scope as well.

Additionally, the above import brings into scope the `jsonBody[T]` body input/output description, which uses the above 
codec.

For example, to automatically generate a JSON codec for a case class:

```scala
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._

case class Book(author: String, title: String, year: Int)

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

Circe lets you select an instance of `io.circe.Printer` to configure the way JSON objects are rendered. By default 
Tapir uses `Printer.nospaces`, which would render:

```scala
import io.circe._

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
import sttp.tapir.json.circe._
import io.circe.Printer

object MyTapirJsonCirce extends TapirJsonCirce {
  override def jsonPrinter: Printer = Printer.spaces2.copy(dropNullValues = true)
}

import MyTapirJsonCirce._
```

Now the above JSON object will render as

```json
{"key1":"present"}
```

## µPickle

To use µPickle add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-upickle" % "0.20.0-M8"
```

Next, import the package (or extend the `TapirJsonuPickle` trait, see [MyTapir](../mytapir.md) and add `TapirJsonuPickle` not `TapirCirceJson`):

```scala
import sttp.tapir.json.upickle._
```

µPickle requires a ReadWriter in scope for each type you want to serialize. In order to provide one use the `macroRW` macro in the companion object as follows:

```scala
import sttp.tapir._
import sttp.tapir.generic.auto._
import upickle.default._
import sttp.tapir.json.upickle._

case class Book(author: String, title: String, year: Int)

object Book {
  implicit val rw: ReadWriter[Book] = macroRW
}

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

Like Circe, µPickle allows you to control the rendered json output. Please see the [Custom Configuration](https://com-lihaoyi.github.io/upickle/#CustomConfiguration) of the manual for details.

For more examples, including making a custom encoder/decoder, see [TapirJsonuPickleTests.scala](https://github.com/softwaremill/tapir/blob/master/json/upickle/src/test/scala/sttp/tapir/json/upickle/TapirJsonuPickleTests.scala)

## Play JSON

To use Play JSON add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-play" % "0.20.0-M8"
```

Next, import the package (or extend the `TapirJsonPlay` trait, see [MyTapir](../mytapir.md) and add `TapirJsonPlay` not `TapirCirceJson`):

```scala
import sttp.tapir.json.play._
```

Play JSON requires `Reads` and `Writes` implicit values in scope for each type you want to serialize. 

## Spray JSON

To use Spray JSON add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-spray" % "0.20.0-M8"
```

Next, import the package (or extend the `TapirJsonSpray` trait, see [MyTapir](../mytapir.md) and add `TapirJsonSpray` not `TapirCirceJson`):

```scala
import sttp.tapir.json.spray._
```

Spray JSON requires a `JsonFormat` implicit value in scope for each type you want to serialize. 

## Tethys JSON

To use Tethys JSON add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-tethys" % "0.20.0-M8"
```

Next, import the package (or extend the `TapirJsonTethys` trait, see [MyTapir](../mytapir.md) and add `TapirJsonTethys` not `TapirCirceJson`):

```scala
import sttp.tapir.json.tethysjson._
```

Tethys JSON requires `JsonReader` and `JsonWriter` implicit values in scope for each type you want to serialize. 

## Jsoniter Scala

To use [Jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % "0.20.0-M8"
```

Next, import the package (or extend the `TapirJsonJsoniter` trait, see [MyTapir](../mytapir.md) and add `TapirJsonJsoniter` not `TapirCirceJson`):

```scala
import sttp.tapir.json.jsoniter._
```

Jsoniter Scala requires `JsonValueCodec` implicit value in scope for each type you want to serialize. 


## Json4s

To use [json4s](https://github.com/json4s/json4s) add the following dependencies to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-json4s" % "0.20.0-M8"
```

And one of the implementations:

```scala
"org.json4s" %% "json4s-native" % "4.0.4"
// Or
"org.json4s" %% "json4s-jackson" % "4.0.4"
```

Next, import the package (or extend the `TapirJson4s` trait, see [MyTapir](../mytapir.md) and add `TapirJson4s` instead of `TapirCirceJson`):

```scala
import sttp.tapir.json.json4s._
```

Json4s requires `Serialization` and `Formats` implicit values in scope, for example:

```scala
import org.json4s._
// ...
implicit val serialization: Serialization = org.json4s.jackson.Serialization
implicit val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)
```

## Zio JSON

To use Zio JSON, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "0.20.0-M8"
```
Next, import the package (or extend the `TapirJsonZio` trait, see [MyTapir](../mytapir.md) and add `TapirJsonZio` instead of `TapirCirceJson`):

```scala
import sttp.tapir.json.zio._
```

Zio JSON requires `JsonEncoder` and `JsonDecoder` implicit values in scope for each type you want to serialize.

## Other JSON libraries

To add support for additional JSON libraries, see the
[sources](https://github.com/softwaremill/tapir/blob/master/json/circe/src/main/scala/sttp/tapir/json/circe/TapirJsonCirce.scala)
for the Circe codec (which is just a couple of lines of code).

## Schemas

To derive json codecs automatically, not only implicits from the base library are needed (e.g. a circe 
`Encoder`/`Decoder`), but also an implicit `Schema[T]` value, which provides a mapping between a type `T` and its
schema. A schema-for value contains a single `schema: Schema` field.

See [custom types](customtypes.md) for details.

## Next

Read on about [working with forms](forms.md).
