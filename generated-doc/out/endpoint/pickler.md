# JSON Pickler

Pickler is an experimental module that simplifies working with JSON, using a consistent configuration API to provide both accurate endpoint documentation and server or client-side encoding/decoding. 

In [other](json.md) tapir-JSON integrations, you have to keep the `Schema` (which is used for documentation) in sync with the library-specific configuration of JSON encoders/decoders. The more customizations you need, like special field name encoding, or preferred way to represent sealed hierarchies, the more configuration you need to repeat (which is specific to the chosen library, like µPickle, Circe, etc.).  

`Pickler[T]` takes care of this, generating a consistent pair of `Schema[T]` and `JsonCodec[T]`, with single point of customization. Underneath it uses [µPickle](https://com-lihaoyi.github.io/upickle/) as its specific library for handling JSON, but it aims to keep it as an implementation detail.

To use pickler, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-pickler" % "1.11.8"
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

A pickler also be derived using the `derives` keyword directly on a class:

```scala
import sttp.tapir.json.pickler.*

case class Book(author: String, title: String, year: Int) derives Pickler
val pickler: Pickler[Book] = summon[Pickler[Book]]
```

Picklers for primitive types are available out-of-the-box. For more complex hierarchies, like nested `case class` structures or `enum`s, you'll need to provide picklers for all children (fields, enum cases etc.). Alternatively, you can use automatic derivation described below.

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

It is possible to configure schema and codec derivation by providing an implicit `sttp.tapir.pickler.PicklerConfiguration`. This configuration allows switching field naming policy to `snake_case`, `kebab_case`, or an arbitrary transformation function, as well as setting the field name/value for the coproduct (sealed hierarchy) type discriminator, which is discussed in details in further sections.

```scala
import sttp.tapir.json.pickler.PicklerConfiguration

given customConfiguration: PicklerConfiguration = 
  PicklerConfiguration
    .default
    .withSnakeCaseMemberNames
```

## Enums / sealed traits / coproducts

Pickler derivation for coproduct types (enums with parameters / sealed hierarchies) works automatically, by adding a `$type` discriminator field with the short class name. 

```scala
import sttp.tapir.json.pickler.PicklerConfiguration

// encodes a case object as { "$type": "MyType" }
given PicklerConfiguration = PicklerConfiguration.default
```

This behavior can be overridden either by changing the discriminator field name, or by using custom logic to get field value from base trait.
Selaed hierarchies with all cases being objects are treated differently, considered as [enumerations](#enumerations).

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

The discriminator will be added as a field to all coproduct child codecs and schemas, if it’s not yet present. The schema of the added field will always be a Schema.string. Finally, the mapping between the discriminator field values and the child schemas will be generated using `Configuration.toDiscriminatorValue(childSchemaName)`.

Finally, if the discriminator is a field that’s defined on the base trait (and hence in each implementation), the schemas can be specified as a custom implicit value using the `Pickler.oneOfUsingField` macro, for example (this will also generate the appropriate mappings):

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

## Customising derived schemas

Schemas generated by picklers can be customized using annotations, just like with traditional schema derivation (see [here](schemas.html#using-annotations)). Some annotations automatically affect JSON codes:

* `@encodedName` determines JSON field name
* `@default` sets default value if the field is missing in JSON

## Enumerations

Tapir schemas and JSON codecs treats following cases as "enumerations":
1. Scala 3 `enums`, where all cases are parameterless
2. Sealed hierarchies (coproducts), where all cases are case objects

Such types are handled by `Pickler.derived[T]`: possible values are encoded as simple strings representing the case objects. For example:

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

## Using existing µPickle Readers and Writers

If you have a case where you would like to use an existing custom `ReadWriter[T]`, you can still derive a `Pickler[T]`, 
but you have to provide both your `ReadWriter[T]` and a `Schema[T]` in the given (implicit) scope. With such a setup, 
you can proceed with `Pickler.derived[T]`.

## Divergences from default µPickle behavior

* Tapir pickler serialises fields of type `Option[T]` as direct value `T` or skips serialisation if field value is `None`. 
  This default behavior can be changed by setting `.withTransientNone(false)` in `PicklerConfiguration`, which would result in serialising `None` as `null`.
  This differs from uPickle, where optional values are wrapped in arrays.
* Value classes (case classes extending AnyVal) will be serialised as simple values
* Discriminator field value is a short class name, instead of full package with class name

