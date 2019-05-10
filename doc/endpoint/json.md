# Working with JSON

Json values are supported through codecs which encode/decode values to json strings. However, third-party libraries are
needed for actual json parsing/printing. Currently, [Circe](https://github.com/circe/circe) is supported. To use, add
the following dependency to your project:

```scala
"com.softwaremill.tapir" %% "tapir-json-circe" % "0.7.9"
```

Next, import the package (or extend the `TapirJsonCirce` trait, see [MyTapir](../mytapir.html)):

```scala
import tapir.json.circe._
```

This will bring into scope `Codec`s which, given an in-scope circe `Encoder`/`Decoder` and a `SchemaFor`, will create a
codec using the json media type. Circe includes a couple of approaches to generating encoders/decoders (manual,
semi-auto and auto), so you may choose whatever suits you.

For example, to automatically generate a JSON codec for a case class:

```scala
import tapir._
import tapir.json.circe._
import io.circe.generic.auto._

case class Book(author: String, title: String, year: Int)

val bookInput: EndpointIO[Book] = jsonBody[Book]
```

To add support for other JSON libraries, see the 
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
