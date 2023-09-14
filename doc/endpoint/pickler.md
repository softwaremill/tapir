# JSON Pickler

Pickler is a module that simplifies working with `Schema` and `JSON` without worrying of consistency between these two models. In standard handling, you have to keep schema in sync with JSON codec configuration. The more customizations you need, like special field name encoding, or preferred way to represent sealed hierarchies, the more you need to carefully keep schemas in sync with your specific JSON codec configuration (specific to chosen library, like µPickle, Circe, etc.).  
`Pickler[T]` takes care of this, generating a consistent pair of `Schema[T]` and `JsonCodec[T]`, with single point of customization. Underneath it uses µPickle as its specific library for handling JSON, but it aims to keep it as an implementation detail.

To use picklers, add the following dependency to your project:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-json-pickler" % "@VERSION@"
```

Please note that it is avilable only for Scala 3 and Scala.js 3.

## Semi-automatic derivation

A pickler can be derived directly using `Pickler.derived[T]`. This will derive both schema and `JsonCodec[T]`:

```scala mdoc:compile-only
import sttp.tapir.json.pickler.*

case class Book(author: String, title: String, year: Int)

val pickler: Pickler[Book] = Pickler.derived
val codec: JsonCodec[Book] = pickler.toCodec
val bookJsonStr = // { "author": "Herman Melville", "title": Moby Dick", "year": 1851 }
  codec.encode(Book("Herman Melville", "Moby Dick", 1851))
```

A `given` Pickler in scope makes it available for `jsonQuery`, `jsonBody` and `jsonBodyWithRaw`, as long as the proper import is in place:

```scala mdoc:compile-only
import sttp.tapir.*
import sttp.tapir.json.pickler.*

case class Book(author: String, title: String, year: Int)

given Pickler[Book] = Pickler.derived

val bookQuery: EndpointInput.Query[Book] = jsonQuery[Book]("book")
```

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

It can also be derived using the `derives` keyword directly on a class:

```scala mdoc:compile-only
import sttp.tapir.json.pickler.*

case class Book(author: String, title: String, year: Int) derives Pickler
val pickler: Pickler[Book] = summon[Pickler]
```

## Automatic derivation

Similarly to traditional typeclass derivation schemes, you can either provide picklers for individual classes which compose into more complex classes, or rely on generic auto-derivation using a dedicated import:

```scala mdoc:compile-only
import sttp.tapir.json.pickler.*
import sttp.tapir.json.pickler.generic.auto.*

sealed trait Country
case object India extends Country
case object Bhutan extends Country

case class Address(street: String, zipCode: String, country: Country)
case class Person(name: String, address: Address)

val pickler: Pickler[Person] = summon[Pickler[Person]]
```

## Configuring Pickler derivation

It is possible to configure schema and codec derivation by providing an implicit `sttp.tapir.generic.Configuration`, just as for standalone [schema derivation](schemas.md). This configuration allows switching field naming policy to `snake_case`, `kebab_case`, or an arbitrary transformation function, as well as setting field name for coproduct (sealed hierarchy) type discriminator, which is discussed in details in further sections.

```scala mdoc:compile-only
import sttp.tapir.generic.Configuration

given customConfiguration: Configuration = Configuration.default.withSnakeCaseMemberNames
```

## Sealed traits / coproducts

Pickler derivation for coproduct types (sealed hierarchies) works automatically, by adding mentioned discriminator `$type` field with full class name. This is the default behavior of uPickle, but it can be overridden either by changing the discriminator field name, or by using custom logic to get field value from base trait.

A discriminator field can be specified for coproducts by providing it in the configuration; this will be only used during automatic and semi-automatic derivation:

```scala mdoc:compile-only
import sttp.tapir.generic.Configuration

given customConfiguration: Configuration =
  Configuration.default.withDiscriminator("who_am_i")
```

The discriminator will be added as a field to all coproduct child codecs and schemas, if it’s not yet present. The schema of the added field will always be a Schema.string. Finally, the mapping between the discriminator field values and the child schemas will be generated using `Configuration.toDiscriminatorValue(childSchemaName)`.

Finally, if the discriminator is a field that’s defined on the base trait (and hence in each implementation), the schemas can be specified as a custom implicit value using the `Pickler.oneOfUsingField` macro, for example (this will also generate the appropriate mappings):

```scala mdoc:compile-only
sealed trait Entity {
  def kind: String
}
case class Person(firstName: String, lastName: String) extends Entity {
  def kind: String = "person"
}
case class Organization(name: String) extends Entity {
  def kind: String = "org"
}

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

- `@encodedName` determines JSON field name
- `@default` sets default value if the field is missing in JSON

## Enums

Scala 3 enums can be automatically handled by `Pickler.derived[T]`. This will encode enum values as simple strings representing type name. For example:

```scala mdoc:compile-only
import sttp.tapir.json.pickler.*

enum ColorEnum:
  case Green, Pink

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

If you need to customize enum value encoding, use `Pickler.derivedEnumeration[T]`:

```scala mdoc:compile-only
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

If you have a case where you would like to use an already defined `upickle.default.ReadWriter[T]`, you can still derive a `Pickler[T]`, but you have to provide both your `ReadWriter[T]` and a `Schema[T]` in implicit scope. With such a setup, you can proceed with `Pickler.derived[T]`.

## Divergences from default µPickle behavior

* Tapir Pickler serialises None values as `null`, instead of wrapping the value in an array
* Value classes (case classes extending AnyVal) will be serialised as simple values

