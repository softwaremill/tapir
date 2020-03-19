# Working with JSON

Json values are supported through codecs which encode/decode values to json strings. However, third-party libraries are
needed for actual json parsing/printing. Currently, [Circe](https://github.com/circe/circe), 
[µPickle](http://www.lihaoyi.com/upickle/) and [Play JSON](https://github.com/playframework/play-json) are supported.

## Circe

To use Circe add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "0.12.25"
```

Next, import the package (or extend the `TapirJsonCirce` trait, see [MyTapir](../mytapir.html)):

```scala
import sttp.tapir.json.circe._
```

This will allow automatically deriving `Codec`s which, given an in-scope circe `Encoder`/`Decoder` and a `Schema`, 
will create a codec using the json media type. Circe includes a couple of approaches to generating encoders/decoders 
(manual, semi-auto and auto), so you may choose whatever suits you.

Note that when using Circe's auto derivation, any encoders/decoders for custom types must be in scope as well.

For example, to automatically generate a JSON codec for a case class:

```scala
import sttp.tapir._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

case class Book(author: String, title: String, year: Int)

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

Circe lets you select an instance of `io.circe.Printer` to configure the way JSON objects are rendered. By default 
Tapir uses `Printer.nospaces`, which would render:

```scala
Json.obj(
  "key1" -> Json.fromString("present"),
  "key2" -> Json.Null
)
```

as

```
{"key1":"present","key2":null}
```

Suppose we would instead want to omit `null`-values from the object and pretty-print it. You can configure this by 
overriding the `jsonPrinter` in `tapir.circe.json.TapirJsonCirce`:

```scala
object MyTapirJsonCirce extends TapirJsonCirce {
  override def jsonPrinter: Printer = Printer.spaces2.copy(dropNullValues = true)
}

import MyTapirJsonCirce._
```

Now the above JSON object will render as

```
{"key1":"present"}
```

## µPickle

To use µPickle add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-upickle" % "0.12.25"
```

Next, import the package (or extend the `TapirJsonuPickle` trait, see [MyTapir](../mytapir.html) and add `TapirJsonuPickle` not `TapirCirceJson`):

```scala
import sttp.tapir.json.upickle._
```

µPickle requires a ReadWriter in scope for each type you want to serialize. In order to provide one use the `macroRW` macro in the companion object as follows:

```scala
import sttp.tapir._
import upickle.default._
import sttp.tapir.json.upickle._

case class Book(author: String, title: String, year: Int)

object Book {
  implicit val rw: ReadWriter[Book] = macroRW
}

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

Like Circe, µPickle allows you to control the rendered json output. Please see the [Custom Configuration](http://www.lihaoyi.com/upickle/#CustomConfiguration) of the manual for details.

For more examples, including making a custom encoder/decoder, see [TapirJsonuPickleTests.scala](https://github.com/softwaremill/tapir/blob/master/json/upickle/src/test/scala/tapir/json/upickle/TapirJsonuPickleTests.scala)

## Play JSON

To use Play JSON add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-play" % "0.12.25"
```

Next, import the package (or extend the `TapirJsonPlay` trait, see [MyTapir](../mytapir.html) and add `TapirJsonPlay` not `TapirCirceJson`):

```scala
import sttp.tapir.json.play._
```

Play JSON requires `Reads` and `Writes` implicit values in scope for each type you want to serialize. 

## Spray JSON

To use Spray JSON add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-spray" % "0.12.25"
```

Next, import the package (or extend the `TapirJsonSpray` trait, see [MyTapir](../mytapir.html) and add `TapirJsonSpray` not `TapirCirceJson`):

```scala
import sttp.tapir.json.spray._
```

Spray JSON requires a `JsonFormat` implicit value in scope for each type you want to serialize. 

## Other JSON libraries

To add support for additional JSON libraries, see the
[sources](https://github.com/softwaremill/tapir/blob/master/json/circe/src/main/scala/sttp/tapir/json/circe/TapirJsonCirce.scala)
for the Circe codec (which is just a couple of lines of code).

## Schemas

To derive json codecs automatically, not only implicits from the base library are needed (e.g. a circe 
`Encoder`/`Decoder`), but also an implicit `SchemaFor[T]` value, which provides a mapping between a type `T` and its 
schema. A schema-for value contains a single `schema: Schema` field.

See [custom types](customtypes.html) for details.

## Next

Read on about [working with forms](forms.html).
