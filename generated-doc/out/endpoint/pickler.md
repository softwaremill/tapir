# JSON Pickler

Pickler is an experimental module that simplifies working with JSON, using a consistent configuration API to provide both accurate endpoint documentation and server or client-side encoding/decoding.

In [other](json.md) tapir-JSON integrations, you have to keep the `Schema` (which is used for documentation) in sync with the library-specific configuration of JSON encoders/decoders. The more customizations you need, like special field name encoding, or preferred way to represent sealed hierarchies, the more configuration you need to repeat (which is specific to the chosen library, like Circe, jsoniter-scala, etc.).

`Pickler[T]` takes care of this, generating a consistent pair of `Schema[T]` and `JsonCodec[T]` with a single point of customization. Both are produced by a **single macro expansion** from the same configuration, so the documented schema and the actual JSON cannot drift apart. Underneath, the JSON is handled by [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) codecs, but this is an implementation detail: the derivation decides every field name and discriminator value, and jsoniter generates the reader/writer for them.

To use pickler, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-pickler" % "1.13.31"
```

Please note that it is available only for Scala 3 and Scala.JS 3.

## Semi-automatic derivation

A pickler can be derived directly using `Pickler.derived[T]`. This will derive both schema and `JsonCodec[T]`:

```scala 
import sttp.tapir.json.pickler.*
import sttp.tapir.Codec.JsonCodec

case class Book(author: String, title: String, year: Int)

val pickler: Pickler[Book] = Pickler.derived
val codec: JsonCodec[Book] = pickler.toCodec
val bookJsonStr = // { "author": "Herman Melville", "title": Moby Dick", "year": 1851 }
  codec.encode(Book("Herman Melville", "Moby Dick", 1851))
```

A `given` pickler in scope makes it available for `jsonQuery`, `jsonBody` and `jsonBodyWithRaw`, which need to be imported from the `sttp.tapir.json.pickler` package. For example:

```scala
import sttp.tapir.*
import sttp.tapir.json.pickler.*

case class Book(author: String, title: String, year: Int)

given Pickler[Book] = Pickler.derived

val addBook: PublicEndpoint[Book, Unit, Unit, Any] =
  endpoint
    .in("books")
    .in("add")
    .in(jsonBody[Book].description("The book to add"))
```

A pickler can also be derived using the `derives` keyword directly on a class:

```scala
import sttp.tapir.json.pickler.*

case class Book(author: String, title: String) derives Pickler
val pickler: Pickler[Book] = summon[Pickler[Book]]
```

`Pickler.derived[T]` derives the whole type graph reachable from `T` in one go: nested case classes, sealed hierarchies and enums, `Option`s, collections, `Map`s with `String` keys, `Either`s, and the primitive, `java.time`, `UUID` etc. leaf types. You do not need picklers for the nested types — unless you want to customize one of them, in which case a `given Pickler[X]` for a nested `X` is honoured by both the schema and the codec:

```scala
import sttp.tapir.json.pickler.*

case class Address(street: String, zipCode: String)
case class Person(name: String, address: Address)

// the address is written with SCREAMING_SNAKE_CASE fields, the person with the default ones
given Pickler[Address] = Pickler.derived(using PicklerConfiguration.default.withScreamingSnakeCaseMemberNames)
val personPickler: Pickler[Person] = Pickler.derived
```

## Automatic derivation

Picklers can be derived at usage side, when required, by adding the auto-derivation import:

```scala
import sttp.tapir.json.pickler.*
import sttp.tapir.json.pickler.generic.auto.*

enum Country:
  case India
  case Bhutan

case class Address(street: String, zipCode: String, country: Country)
case class Person(name: String, address: Address)

val pickler: Pickler[Person] = summon[Pickler[Person]]
```

However, this can negatively impact compilation performance, as the same pickler might be derived multiple times, for each usage of a type. This can be improved by explicitly providing picklers (as described in the semi-auto section above) either for all, or selected types. It's important then to make sure that the manually-provided picklers are in the implicit scope at the usage sites.

## Configuring pickler derivation

It is possible to configure schema and codec derivation by providing an implicit `sttp.tapir.json.pickler.PicklerConfiguration`. This configuration allows switching field naming policy to `snake_case`, `kebab_case`, or an arbitrary transformation function, as well as setting the field name/value for the coproduct (sealed hierarchy) type discriminator, which is discussed in details in further sections.

```scala
import sttp.tapir.json.pickler.PicklerConfiguration

given customConfiguration: PicklerConfiguration = 
  PicklerConfiguration
    .default
    .withSnakeCaseMemberNames
```

The configuration is read at **compile time**: field names and discriminator values become part of the generated code. The configuration must therefore be a `given` (or `implicit val`) whose right-hand side is built from `PicklerConfiguration.default` and its `with*` methods, defined in the same compilation unit as the derivation, or in an already compiled module. A function passed to `withToEncodedName` must itself be evaluable at compile time (e.g. `_.toUpperCase`). The derivation reports a clear error when it cannot evaluate the configuration.

## Enums / sealed traits / coproducts

Pickler derivation for coproduct types (enums with parameters / sealed hierarchies) works automatically, by adding a `$type` discriminator field with the short class name. 

```scala
import sttp.tapir.json.pickler.PicklerConfiguration

// encodes a case object as { "$type": "MyType" }
given PicklerConfiguration = PicklerConfiguration.default
```

This behavior can be overridden either by changing the discriminator field name, or by using custom logic to get field value from base trait.
Sealed hierarchies with all cases being objects are treated differently, considered as [enumerations](#enumerations).

A discriminator field can be specified for coproducts by providing it in the configuration; this will be only used during automatic and semi-automatic derivation:

```scala
import sttp.tapir.json.pickler.PicklerConfiguration

// encodes a case object as { "who_am_i": "full.pkg.path.MyType" }
given customConfiguration: PicklerConfiguration =
  PicklerConfiguration
    .default
    .withDiscriminator("who_am_i")
    .withFullDiscriminatorValues
```

The discriminator will be added as a field to all coproduct child codecs and schemas, if it's not yet present. The schema of the added field will always be a `Schema.string`. Finally, the mapping between the discriminator field values and the child schemas will be generated using `Configuration.toDiscriminatorValue(childSchemaName)`.

Note that a case class which is a member of a sealed hierarchy carries its discriminator even when it is encoded on its own (through its own pickler), so that the two encodings agree.

Finally, if the discriminator is a field that's defined on the base trait (and hence in each implementation), the discriminator values can be specified explicitly using the `Pickler.oneOfUsingField` macro, for example (this will also generate the appropriate mappings):

```scala
sealed trait Entity:
  def kind: String

case class Person(firstName: String, lastName: String) extends Entity:
  def kind: String = "person"

case class Organization(name: String) extends Entity:
  def kind: String = "org"


import sttp.tapir.json.pickler.*

val pPerson = Pickler.derived[Person]
val pOrganization = Pickler.derived[Organization]
given pEntity: Pickler[Entity] =
  Pickler.oneOfUsingField[Entity, String](_.kind, _.toString)
    ("person" -> pPerson, "org" -> pOrganization)

// { "$type": "person", "firstName": "Jessica", "lastName": "West" }
pEntity.toCodec.encode(Person("Jessica", "West"))
```

The mapping keys and the `asString` function (`_.toString` above) have to be evaluable at compile time, since the resulting values become part of the generated codec. The children's schemas are taken from the given picklers; their codecs are derived with the specified discriminator values. `oneOfUsingField` cannot be used with enumerations (hierarchies of objects only); use `derivedEnumeration` for those.

## Customising derived schemas

Schemas generated by picklers can be customized using annotations, just like with traditional schema derivation (see [here](schemas.md#using-annotations)). Some annotations automatically affect JSON codecs:

* `@encodedName` on a field determines the JSON field name; on a type, it replaces the type's name (and hence its discriminator value)

Note that the `@default` annotation is **documentation only**: it is included in the schema, but a field missing from the JSON is not filled from it. Scala default parameter values *are* used when decoding a JSON object with a missing field.

## Enumerations

Tapir schemas and JSON codecs treat the following cases as "enumerations":
1. Scala 3 `enum`s, where all cases are parameterless (or have parameters, but no case is a case class)
2. Sealed hierarchies (coproducts), where all cases are case objects

Such types are handled by `Pickler.derived[T]`: possible values are encoded as simple strings with the case objects' names. For example:

```scala
import sttp.tapir.json.pickler.*

enum ColorEnum:
  case Green, Pink

// or:
// sealed trait ColorEnum
//   case object Green extends ColorEnum
//   case object Pink extends ColorEnum

case class ColorResponse(color: ColorEnum, description: String)

given Pickler[ColorEnum] = Pickler.derived
val pResponse = Pickler.derived[ColorResponse]

// { "color": "Pink", "description": "Pink desc" }
pResponse.toCodec.encode(
  ColorResponse(ColorEnum.Pink, "Pink desc")
)
// Enumeration schema with proper validator
pResponse.schema
```

The names of enumeration values are not affected by the `with*DiscriminatorValues` configuration methods — an enumeration value is not a discriminator. To render them differently, use `derivedEnumeration` (below).

If sealed hierarchy or enum contain case classes with parameters, they are no longer an "enumeration", and will be treated as standard sealed hierarchies (coproducts):

```scala
import sttp.tapir.json.pickler.*

sealed trait ColorEnum
case object Green extends ColorEnum
case class Pink(intensity: Int) extends ColorEnum

case class ColorResponse(color1: ColorEnum, color2: ColorEnum)

given Pickler[ColorEnum] = Pickler.derived
val pResponse = Pickler.derived[ColorResponse]

// {"color1":{"$type":"Pink","intensity":85},"color2":{"$type":"Green"}}
pResponse.toCodec.encode(
  ColorResponse(Pink(85), Green)
)
```

If you need to customize enumeration value encoding, use `Pickler.derivedEnumeration[T]`:

```scala
import sttp.tapir.json.pickler.*

enum ColorEnum:
  case Green, Pink

case class ColorResponse(color: ColorEnum, description: String)

given Pickler[ColorEnum] = Pickler
  .derivedEnumeration[ColorEnum]
  .customStringBased(_.ordinal.toString)

val pResponse = Pickler.derived[ColorResponse]

// { "color": "1", "description": "Pink desc" }
pResponse.toCodec.encode(
  ColorResponse(ColorEnum.Pink, "Pink desc")
)
// Enumeration schema with proper validator
pResponse.schema
```

The encoding function is applied at runtime (it is not restricted to compile-time evaluable code), and has to give distinct strings to distinct cases.

## Maps, Options, collections, Either

`Map[String, V]` is derived directly, as a JSON object. For other key types, provide a pickler built with `Pickler.picklerForMap`, giving both directions of the key conversion (the first is also used to document the keys in the schema):

```scala
import sttp.tapir.json.pickler.*
import java.util.UUID

case class Book(title: String)
case class Library(books: Map[UUID, Book])

given Pickler[Book] = Pickler.derived
given Pickler[Map[UUID, Book]] = Pickler.picklerForMap(_.toString, UUID.fromString)
val pLibrary = Pickler.derived[Library]
```

Fields of type `Option[T]` are serialised as the direct value `T`, and skipped if the value is `None`. This default behavior can be changed by setting `.withTransientNone(false)` in `PicklerConfiguration`, which results in serialising `None` as `null`. Empty collections are always written (as `[]`). Unknown fields in the JSON are skipped when decoding.

`Either[A, B]` is written as the bare `A` or `B` value (there is no tag), matching tapir's own `Schema[Either[A, B]]` and the `Codec.eitherRight` convention; decoding tries `B` first, then `A`.

Given a `Pickler[T]`, picklers for wrappers can be built with `asOption`, `asIterable[C]` and `asArray`. These are equivalent to deriving the wrapper type directly (`Pickler.derived[List[T]]`), which is what automatic derivation does; there are no implicit picklers for wrappers in scope by default.

## Using existing codecs

If you have a type whose JSON representation is hand-written, or comes from elsewhere, build a pickler from a `Schema[T]` and a jsoniter-scala `JsonValueCodec[T]` and put it in a `given` — it is then honoured by both derivation halves wherever the type is nested:

```scala
import sttp.tapir.Schema
import sttp.tapir.json.pickler.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import java.util.TimeZone

val tzCodec: JsonValueCodec[TimeZone] = new JsonValueCodec[TimeZone] {
  def nullValue: TimeZone = null
  def decodeValue(in: JsonReader, default: TimeZone): TimeZone = TimeZone.getTimeZone(in.readString(null))
  def encodeValue(x: TimeZone, out: JsonWriter): Unit = out.writeVal(x.getID)
}
given Pickler[TimeZone] = Pickler.fromSchemaAndCodec(Schema.string, tzCodec)

case class Meeting(tz: TimeZone)
val pMeeting = Pickler.derived[Meeting] // {"tz":"UTC"}
```

Note that a bare `given JsonValueCodec[X]` for a case class `X`, without a `Pickler[X]`, is rejected at compile time: the schema would still be derived from the class, and the two could disagree. A `Pickler` carries both halves.

## Other notes

* Value classes (case classes extending `AnyVal`) are serialised as the wrapped value, and documented as such.
* Tuples are not supported: a tuple would be documented as an object and written as an array. Use a case class.
* A debugging log of the derivation can be enabled by importing `sttp.tapir.json.pickler.debug.logDerivationForPickler`, or with the scalac option `-Xmacro-settings:tapirPickler.logDerivation=true`. The derivation timeout (5 seconds by default) can be raised with `-Xmacro-settings:tapirPickler.timeout=<seconds>` (or a duration such as `30s`) for very large type graphs.
