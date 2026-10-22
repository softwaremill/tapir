# JSON Pickler

Pickler is an experimental module that simplifies working with JSON, using a consistent configuration API to provide both accurate endpoint documentation and server or client-side encoding/decoding.

In [other](json.md) tapir-JSON integrations, you have to keep the `Schema` (which is used for documentation) in sync with the library-specific configuration of JSON encoders/decoders. The more customizations you need, like special field name encoding, or preferred way to represent sealed hierarchies, the more configuration you need to repeat (which is specific to the chosen library, like Circe, jsoniter-scala, etc.).

`Pickler[T]` derives a `Schema[T]` and a `JsonCodec[T]` together, from one configuration, so the docs always match the JSON. The JSON is read and written by [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala).

To use pickler, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-pickler" % "@VERSION@"
```

Please note that it is available only for Scala 3 and Scala.JS 3.

## Semi-automatic derivation

A pickler can be derived directly using `Pickler.derived[T]`. This will derive both schema and `JsonCodec[T]`:

```scala mdoc:compile-only 
import sttp.tapir.json.pickler.*
import sttp.tapir.Codec.JsonCodec

case class Book(author: String, title: String, year: Int)

val pickler: Pickler[Book] = Pickler.derived
val codec: JsonCodec[Book] = pickler.toCodec
val bookJsonStr = // { "author": "Herman Melville", "title": "Moby Dick", "year": 1851 }
  codec.encode(Book("Herman Melville", "Moby Dick", 1851))
```

A `given` pickler in scope makes it available for `jsonQuery`, `jsonBody` and `jsonBodyWithRaw`, which need to be imported from the `sttp.tapir.json.pickler` package. For example:

```scala mdoc:compile-only
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

```scala mdoc:compile-only
import sttp.tapir.json.pickler.*

case class Book(author: String, title: String) derives Pickler
val pickler: Pickler[Book] = summon[Pickler[Book]]
```

`Pickler.derived[T]` derives the whole type graph reachable from `T` in one go: nested case classes, sealed hierarchies and enums, `Option`s, collections, `Map`s with `String` keys, `Either`s, and the primitive, `java.time`, `UUID` etc. leaf types. You do not need picklers for the nested types — unless you want to customize one of them, in which case a `given Pickler[X]` for a nested `X` is honoured by both the schema and the codec:

```scala mdoc:compile-only
import sttp.tapir.json.pickler.*

case class Address(street: String, zipCode: String)
case class Person(name: String, address: Address)

// the address is written with SCREAMING_SNAKE_CASE fields, the person with the default ones
given Pickler[Address] = Pickler.derived(using PicklerConfiguration.default.withScreamingSnakeCaseMemberNames)
val personPickler: Pickler[Person] = Pickler.derived
```

The override must be a plain `given` or `implicit val`. An `inline given` or `inline def` that calls `Pickler.derived` is not used for nested types. A type alias and the type it aliases are treated as the same type, so a `given Pickler[UUID]` also applies to a field typed `Id` where `type Id = UUID`.

A `given` pickler for a type with a sealed parent (e.g. a case of a sealed trait or enum) is not used when that type is nested in another type; provide one for the parent type instead.

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

`generic.auto.*` derives the whole type graph again at every use site, which slows compilation. Prefer `derives Pickler` or a `given` pickler, at least for the types you use most often.

## Configuring pickler derivation

It is possible to configure schema and codec derivation by providing an implicit `sttp.tapir.json.pickler.PicklerConfiguration`. This configuration allows switching field naming policy to `snake_case`, `kebab_case`, or an arbitrary transformation function, as well as setting the field name/value for the coproduct (sealed hierarchy) type discriminator, which is discussed in details in further sections.

```scala mdoc:compile-only
import sttp.tapir.json.pickler.PicklerConfiguration

inline given customConfiguration: PicklerConfiguration = 
  PicklerConfiguration
    .default
    .withSnakeCaseMemberNames
```

The configuration is evaluated at compile time. Define it as an `inline given`, built from `PicklerConfiguration.default` and its `with*` methods. A non-inline `given` works only if it is local to a method, comes from a compiled library, or `-Yretain-trees` is set. Functions passed to `withToEncodedName` or `toDiscriminatorValue` are also evaluated at compile time. They may only call `java.lang.String` methods (`_.toUpperCase`, `n => n.toLowerCase.concat("_")`); string `+` and `StringOps` (`.reverse`, `.capitalize`) don't work. This also applies to `Pickler.schemaFor[T]`.

## Enums / sealed traits / coproducts

Pickler derivation for coproduct types (enums with parameters / sealed hierarchies) works automatically, by adding a `$type` discriminator field with the short class name. 

```scala mdoc:compile-only
import sttp.tapir.json.pickler.PicklerConfiguration

// encodes a case class MyType as { "$type": "MyType", ...fields }
inline given PicklerConfiguration = PicklerConfiguration.default
```

This behavior can be overridden either by changing the discriminator field name, or by using custom logic to get field value from base trait.
Sealed hierarchies with all cases being objects are treated differently, considered as [enumerations](#enumerations).

A discriminator field can be specified for coproducts by providing it in the configuration; this will be only used during automatic and semi-automatic derivation:

```scala mdoc:compile-only
import sttp.tapir.json.pickler.PicklerConfiguration

// encodes a case class MyType as { "who_am_i": "full.pkg.path.MyType", ...fields }
inline given customConfiguration: PicklerConfiguration =
  PicklerConfiguration
    .default
    .withDiscriminator("who_am_i")
    .withFullDiscriminatorValues 
```

The discriminator will be added as a field to all coproduct child codecs and schemas, if it's not yet present. The schema of the added field will always be a `Schema.string`. Finally, the mapping between the discriminator field values and the child schemas will be generated using `PicklerConfiguration.toDiscriminatorValue(childSchemaName)`.

This also holds when a case class is encoded with its own pickler: the JSON and the schema both include the discriminator.

Finally, if the discriminator is a field that's defined on the base trait (and hence in each implementation), the discriminator values can be specified explicitly using the `Pickler.oneOfUsingField` macro, for example (this will also generate the appropriate mappings):

```scala mdoc:compile-only
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

The mapping keys and the `asString` function (`_.toString` above) have to be evaluable at compile time, since the resulting values become part of the generated codec. The mapping must cover every case of the hierarchy exactly once. The picklers in the mapping only identify which case each value selects: both the schema and the codec of every case are derived here, under the configuration in scope, with the given discriminator values — so a child pickler derived with a different configuration does not change the result. `oneOfUsingField` cannot be used with enumerations (hierarchies of objects only); use `derivedEnumeration` for those.

## Customising derived schemas

Schemas generated by picklers can be customized using annotations, just like with traditional schema derivation (see [here](schemas.md#using-annotations)). Some annotations automatically affect JSON codecs:

* `@encodedName` on a field determines the JSON field name; on a type, it replaces the type's name (and hence its discriminator value)

When decoding, a missing field gets its Scala default value. The schema marks such a field as optional and shows the default. `@default` only changes the documented default, and needs a Scala default value on the field (otherwise compilation fails).

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

```scala mdoc:compile-only
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

```scala mdoc:compile-only
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

Given a `Pickler[T]`, picklers for wrappers can be built with `asOption`, `asIterable[C]` and `asArray`. These are equivalent to deriving the wrapper type directly (`Pickler.derived[List[T]]`), which is what automatic derivation does; there are no `given` picklers for wrappers in scope by default.

## Using existing codecs

If you have a type whose JSON representation is hand-written, or comes from elsewhere, build a pickler from a `Schema[T]` and a jsoniter-scala `JsonValueCodec[T]` and put it in a `given` — it is then honoured by both derivation halves wherever the type is nested:

```scala mdoc:compile-only
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

Note that a bare `given JsonValueCodec[X]` for a case class or sealed hierarchy `X`, without a `Pickler[X]`, is rejected at compile time — whether `X` is nested or the type being derived itself: the schema would still be derived from the class, and the two could disagree. A `Pickler` carries both halves.

## Other notes

* Value classes (case classes extending `AnyVal`) are serialised as the wrapped value, and documented as such.
* Tuples are not supported: a tuple would be documented as an object and written as an array. Use a case class.
* To log the derivation, import `sttp.tapir.json.pickler.debug.logDerivationForPickler`, or set `-Xmacro-settings:tapirPickler.logDerivation=true`.
* The derivation times out after 60 seconds. Change this with `-Xmacro-settings:tapirPickler.timeout=<value>` (seconds, or a duration such as `30s`).
* To change jsoniter-scala's decoding settings, put a `given ReaderConfig` in scope where the codec is created (`jsonBody`, `jsonQuery`, `toCodec`, …), e.g. `given ReaderConfig = Pickler.defaultReaderConfig.withMaxCharBufSize(16 * 1024 * 1024)`. Values inside an `Either` always use `Pickler.defaultReaderConfig`.
* When decoding, a `Map` or `Set` with more than 1024 entries is rejected (jsoniter-scala's guard against hash-collision attacks). To allow more, provide a `given Pickler` for that type using `Pickler.fromSchemaAndCodec`, with a codec that has a higher `mapMaxInsertNumber` / `setMaxInsertNumber`.
* `import sttp.tapir.json.pickler.given` brings a `given JsonCodec[T]` for every `T` with a `Pickler`. If another JSON integration's codecs (e.g. `sttp.tapir.json.circe.*`) are also imported, `JsonCodec[T]` becomes ambiguous for types supported by both.
* `jsoniter-scala-macros` is a compile-time dependency of every project using this module: the generated code calls `JsonCodecMaker.make`, so it has to be on the compile classpath. It is pulled in transitively.

## Migrating from the µPickle-based pickler

Up to and including tapir 1.13.x the pickler module was built on µPickle. The public API is largely unchanged (`Pickler.derived`, `derives Pickler`, `generic.auto.*`, `PicklerConfiguration`, `oneOfUsingField`, `jsonBody`), with the following differences:

* `Pickler[T]` is a trait with `schema` and `codec: JsonValueCodec[T]`, no longer a case class with `innerUpickle`. Custom instances are built with `Pickler.fromSchemaAndCodec`; µPickle `ReadWriter`s cannot be reused.
* There are no `given` picklers for primitives, `AnyVal` classes, `java.math.BigDecimal`/`BigInteger`, `Option`, collections, `Array`, `Either` or `Map[String, _]` (`summon[Pickler[Int]]` doesn't compile). They are derived as part of `Pickler.derived`, or directly, e.g. `Pickler.derived[List[T]]`. `asOption` / `asIterable` / `asArray` remain.
* `generic.auto.picklerForCaseClass` is renamed to `generic.auto.picklerForType`.
* Tuples are no longer supported; use a case class.
* `derivedEnumeration[T].apply(encode, schemaType, default)` is removed; use `defaultStringBased` or `customStringBased`.
* `Pickler.picklerForMap` takes the key parser as a second argument: `picklerForMap(_.toString, UUID.fromString)`.
* `PicklerConfiguration` (and any function inside it) must be evaluable at compile time; see [Configuring pickler derivation](#configuring-pickler-derivation).
* `Either[A, B]` is written **untagged** (the bare `A` or `B` value) instead of `[0, a]` / `[1, b]`, matching tapir's `Schema[Either]` and `Codec.eitherRight`.
* `@default` no longer fills missing fields when decoding, and now needs a Scala default value on the field. Scala default values are now shown in the schema.
* `oneOfUsingField` must map every case of the hierarchy, and derives the cases itself: the picklers passed in the mapping only identify the cases.
* Schema names of classes defined inside a method no longer contain a `<local ...>` segment, matching tapir core's `Schema.derived`.
* Recursive types (directly, through collections/`Option`, or through a sealed hierarchy) are supported.
* Maps and sets with more than 1024 entries are rejected when decoding (µPickle had no limit), see [Other notes](#other-notes).
