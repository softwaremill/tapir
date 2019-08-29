# Working with JSON

Json values are supported through codecs which encode/decode values to json strings. However, third-party libraries are
needed for actual json parsing/printing. Currently, [Circe](https://github.com/circe/circe) and [µPickle](http://www.lihaoyi.com/upickle/) are supported.

## Circe

To use Circe add the following dependency to your project:

```scala
"com.softwaremill.tapir" %% "tapir-json-circe" % "0.10.0"
```

Next, import the package (or extend the `TapirJsonCirce` trait, see [MyTapir](../mytapir.html)):

```scala
import tapir.json.circe._
```

This will bring into scope `Codec`s which, given an in-scope circe `Encoder`/`Decoder` and a `SchemaFor`, will create a
codec using the json media type. Circe includes a couple of approaches to generating encoders/decoders (manual,
semi-auto and auto), so you may choose whatever suits you.

Note that when using Circe's auto derivation, any encoders/decoders for custom types must be in scope as well.

For example, to automatically generate a JSON codec for a case class:

```scala
import tapir._
import tapir.json.circe._
import io.circe.generic.auto._

case class Book(author: String, title: String, year: Int)

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

Circe lets you select an instance of `io.circe.Printer` to configure the way JSON objects are rendered. By default Tapir uses `Printer.nospaces`, which would render
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
Suppose we would instead want to omit `null`-values from the object and pretty-print it.
You can configure this by overriding the `jsonPrinter` in `tapir.circe.json.TapirJsonCirce`:
```scala
object MyTapirJsonCirce extends TapirJsonCirce {
  override def jsonPrinter: Printer = Printer.spaces2.copy(dropNullValues = true)
}

import MyTapirJsonCirce._
```
Now the above JSON object will render as
```
{
  "key1":"present"
}
```

## µPickle

To use µPickle add the following dependency to your project:

```scala
"com.softwaremill.tapir" %% "tapir-json-upickle" % "0.10.0"
```

Next, import the package (or extend the `TapirJsonuPickle` trait, see [MyTapir](../mytapir.html) and add `TapirJsonuPickle` not `TapirCirceJson`):

```scala
import tapir.json.upickle._
```

µPickle requires a ReadWriter in scope for each type you want to serialize. In order to provide one use the `macroRW` macro in the companion object as follows:

```scala
import tapir._
import upickle.default._
import tapir.json.upickle._

case class Book(author: String, title: String, year: Int)

object Book {
  implicit val rw: ReadWriter[Book] = macroRW
}

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

Like Circe, µPickle allows you to control the rendered json output. Please see the [Custom Configuration](http://www.lihaoyi.com/upickle/#CustomConfiguration) of the manual for details.

For more examples, including making a custom encoder/decoder, see [TapirJsonuPickleTests.scala](https://github.com/softwaremill/tapir/blob/master/json/upickle/src/test/scala/tapir/json/upickle/TapirJsonuPickleTests.scala)

## Other JSON libraries

To add support for additional JSON libraries, see the
[sources](https://github.com/softwaremill/tapir/blob/master/json/circe/src/main/scala/tapir/json/circe/TapirJsonCirce.scala)
for the Circe codec (which is just a couple of lines of code).

## Schemas

To create a json codec automatically, not only a circe `Encoder`/`Decoder` is needed, but also an implicit
`SchemaFor[T]` value, which provides a mapping between a type `T` and its schema. A schema-for value contains a single
`schema: Schema` field.

For custom types, schemas are derived automatically using [Magnolia](https://propensive.com/opensource/magnolia/), given
that schemas are defined for all of the case class's fields. It is possible to configure the automatic derivation to use
snake-case, kebab-case or a custom field naming policy, by providing an implicit `tapir.generic.Configuration` value:

```scala
implicit val customConfiguration: Configuration =
  Configuration.default.withSnakeCaseMemberNames
```

Alternatively, `SchemaFor` values can be defined by hand, either for whole case classes, or only for some of its fields.
For example, here we state that the schema for `MyCustomType` is a `String`:

```
implicit val schemaForMyCustomType: SchemaFor[MyCustomType] = SchemaFor(Schema.SString)
```

## Next

Read on about [working with forms](forms.html).
