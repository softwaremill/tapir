# Schema derivation

A schema describes the shape of a value, how the low-level representation should be structured. Schemas are primarily
used when generating [documentation](../docs/openapi.md) and when [validating](validation.md) incoming values.

Schemas are typically defined as implicit values. They are part of [codecs](codecs.md), and are looked up in the 
implicit scope during codec derivation, as well as when using [json](json.md) or [form](forms.md) bodies.

Implicit schemas for basic types (`String`, `Int`, etc.), and their collections (`Option`, `List`, `Array` etc.) are
defined out-of-the box. They don't contain any meta-data, such as descriptions or example values.

There's also a number of [datatype integrations](integrations.md) available, which provide schemas for various 
third-party libraries.

For case classes and sealed hierarchies, `Schema[_]` values can be derived automatically using
[Magnolia](https://github.com/softwaremill/magnolia), given that implicit schemas are available for all the case class's
fields, or all of the implementations of the `enum`/`sealed trait`/`sealed class`.

Two policies of custom type derivation are available:

* automatic derivation
* semi automatic derivation

Finally, schemas can be provided by hand, e.g. for Java classes, or other custom types. As a fallback, you can also
always use `Schema.string[T]` or `Schema.binary[T]`, however this will provide only basic documentation, and won't
perform any [validation](validation.md).

## Automatic derivation

Schemas for case classes, sealed traits and their children can be recursively derived. Importing `sttp.tapir.generic.auto._` 
(or extending the `SchemaDerivation` trait) enables fully automatic derivation for `Schema`:

```scala
import sttp.tapir.Schema
import sttp.tapir.generic.auto._

case class Parent(child: Child)
case class Child(value: String)

// implicit schema used by codecs
implicitly[Schema[Parent]]
```

If you have a case class which contains some non-standard types (other than strings, number, other case classes,
collections), you only need to provide implicit schemas for them. Using these, the rest will be derived automatically.

Note that when using [datatypes integrations](integrations.md), respective schemas & codecs must also be imported to 
enable the derivation, e.g. for [newtype](integrations.html#newtype-integration) you'll have to add
`import sttp.tapir.codec.newtype._` or extend `TapirCodecNewType`.

## Semi-automatic derivation

Semi-automatic derivation can be done using `Schema.derived[T]`.

It only derives selected type `T`. However, derivation is not recursive: schemas must be explicitly defined for every
child type.

This mode is easier to debug and helps to avoid issues encountered by automatic mode (wrong schemas for value classes
or custom types):

```scala
import sttp.tapir.Schema

case class Parent(child: Child)
case class Child(value: String)

implicit lazy val sChild: Schema[Child] = Schema.derived
implicit lazy val sParent: Schema[Parent] = Schema.derived
```

Note that while schemas for regular types can be safely defined as `val`s, in case of recursive values, the schema
values must be `lazy val`s.

## Debugging schema derivation

When deriving schemas using `Schema.derived[T]`, in case derivation fails, you'll get information for which part of `T` 
the schema cannot be found (e.g. a specific field, or a trait subtype). Given this diagnostic information you can drill
down, and try to derive the schema (again using `Schema.derived`) for the problematic part. Eventually, you'll find the 
lowest-level type for which the schema cannot be derived. You might need to provide it manually, or use some kind of
integration layer.

This method may be used both with automatic and semi-automatic derivation. 

## Scala3-specific derivation

### Derivation for recursive types

In Scala3, any schemas for recursive types need to be provided as typed `implicit def` (not a `given`)!
For example:

```scala
case class RecursiveTest(data: List[RecursiveTest])
object RecursiveTest {
  implicit def f1Schema: Schema[RecursiveTest] = Schema.derived[RecursiveTest]
}
```

The implicit doesn't have to be defined in the companion object, just anywhere in scope. This applies to cases where
the schema is looked up implicitly, e.g. for `jsonBody`.

### Derivation for union types

Schemas for union types must be declared by hand, using the `Schema.derivedUnion[T]` method. Schemas for all components
of the union type must be available in the implicit scope at the point of invocation. For example:

```scala
val s: Schema[String | Int] = Schema.derivedUnion
```

If the union type is a named alias, the type needs to be provided explicitly, e.g.:

```scala
type StringOrInt = String | Int
val s: Schema[StringOrInt] = Schema.derivedUnion[StringOrInt]
```

If any of the components of the union type is a generic type, any of its validations will be skipped when validating
the union type, as it's not possible to generate a runtime check for the generic type.

### Derivation for string-based constant union types
e.g. `type AorB = "a" | "b"`

See [enumerations](enumerations.md#scala-3-string-based-constant-union-types-to-enum) on how to use string-based unions of constant types as enums.

## Configuring derivation

It is possible to configure Magnolia's automatic derivation to use `snake_case`, `kebab-case` or a custom field naming
policy, by providing an implicit `sttp.tapir.generic.Configuration` value. This influences how the low-level
representation is described in documentation:

```scala
import sttp.tapir.generic.Configuration

implicit val customConfiguration: Configuration =
  Configuration.default.withSnakeCaseMemberNames
```

## Manually providing schemas

Alternatively, `Schema[_]` values can be defined by hand, either for whole case classes, or only for some of its fields.
For example, here we state that the schema for `MyCustomType` is a `String`:

```scala
import sttp.tapir._

case class MyCustomType()
implicit val schemaForMyCustomType: Schema[MyCustomType] = Schema.string
// or, if the low-level representation is e.g. a number
implicit val anotherSchemaForMyCustomType: Schema[MyCustomType] = Schema(SchemaType.SInteger())
```

## Sealed traits / coproducts

Schema derivation for coproduct types (sealed hierarchies) is supported as well. By default, such hierarchies
will be represented as a coproduct which contains a list of child schemas, without any discriminators.

```{note}
Note that whichever approach you choose to define the coproduct schema, it has to match the way the value is 
encoded and decoded by the codec. E.g. when the schema is for a json body, the discriminator must be separately
configured in the json library, matching the configuration of the schema.

Alternatively, instead of deriving schemas and json codecs separately, you can use the experimental
[pickler](https://tapir.softwaremill.com/en/latest/endpoint/pickler.html) 
module, which provides a higher level `Pickler` concept, which takes care of consistent derivation.  
```

### Field discriminators

A discriminator field can be specified for coproducts by providing it in the configuration; this will be only used
during automatic and semi-automatic derivation:

```scala
import sttp.tapir.generic.Configuration

implicit val customConfiguration: Configuration =
  Configuration.default.withDiscriminator("who_am_i")
```

The discriminator will be added as a field to all coproduct child schemas, if it's not yet present. The schema of
the added field will always be a `Schema.string`. Finally, the mapping between the discriminator field values and
the child schemas will be generated using `Configuration.toDiscriminatorValue(childSchemaName)`.

Alternatively, derived schemas can be customised (see also below), and a discriminator can be added by calling
the `SchemaType.SCoproduct.addDiscriminatorField(name, schema, maping)` method. This method is useful when using
semi-automatic or automatic derivation; in both cases a custom implicit has to be defined, basing on the derived
one:

```scala
import sttp.tapir._
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._

sealed trait MyCoproduct 
case class Child1(s: String) extends MyCoproduct
// ... implementations of MyCoproduct ...

implicit val myCoproductSchema: Schema[MyCoproduct] = {
  val derived = implicitly[Derived[Schema[MyCoproduct]]].value
  derived.schemaType match {
    case s: SchemaType.SCoproduct[_] => derived.copy(schemaType = s.addDiscriminatorField(
      FieldName("myField"),
      Schema.string,
      Map(
        "value1" -> SchemaType.SRef(Schema.SName("com.myproject.Child1")),
        // ... other mappings ...
      )
    ))
    case _ => ???
  }
}
```

Finally, if the discriminator is a field that's defined on the base trait (and hence in each implementation), the
schemas can be specified as a custom implicit value using the `Schema.oneOfUsingField` macro, 
for example (this will also generate the appropriate mappings):

```scala
sealed trait Entity {
  def kind: String
} 
case class Person(firstName: String, lastName: String) extends Entity { 
  def kind: String = "person"
}
case class Organization(name: String) extends Entity {
  def kind: String = "org"  
}

import sttp.tapir._

val sPerson = Schema.derived[Person]
val sOrganization = Schema.derived[Organization]
implicit val sEntity: Schema[Entity] = 
    Schema.oneOfUsingField[Entity, String](_.kind, _.toString)(
      "person" -> sPerson, "org" -> sOrganization)
```

### Wrapper object discriminators

Another discrimination strategy uses a wrapper object. Such an object contains a single field, with its name 
corresponding to the discriminator value. A schema can be automatically generated using the `Schema.oneOfWrapped`
macro, for example:

```scala
sealed trait Entity
case class Person(firstName: String, lastName: String) extends Entity
case class Organization(name: String) extends Entity 

import sttp.tapir._
import sttp.tapir.generic.auto._ // to derive child schemas

implicit val sEntity: Schema[Entity] = Schema.oneOfWrapped[Entity]
```

The names of the field in the wrapper object will be generated using the implicit `Configuration`. If for some reason
this is insufficient, you can generate schemas for individual wrapper objects using `Schema.wrapWithSingleFieldProduct`.

## Customising derived schemas

### Using annotations

In some cases, it might be desirable to customise the derived schemas, e.g. to add a description to a particular
field of a case class. One way the automatic & semi-automatic derivation can be customised is using annotations:

* `@encodedName` sets name for case class's field which is used in the encoded form (and also in documentation)
* `@description` sets description for the whole case class or its field
* `@default` sets default value for a case class field (plus an optional encoded form used in documentation)
* `@encodedExample` sets example value for a case class field which is used in the documentation in the encoded form
* `@format` sets the format for a case class field
* `@deprecated` marks a case class's field as deprecated
* `@validate` will add the given validator to a case class field
* `@validateEach` will add the given validator to the elements of a case class field. Useful for validating the
  value contained in an `Option` (when it's defined), and collection elements

These annotations will adjust schemas, after they are looked up using the normal implicit mechanisms.

### Using implicits

If the target type isn't accessible or can't be modified, schemas can be customized by looking up an implicit instance
of the `Derived[Schema[T]]` type, modifying the value, and assigning it to an implicit schema.

When such an implicit `Schema[T]` is in scope will have higher priority than the built-in low-priority conversion
from `Derived[Schema[T]]` to `Schema[T]`.

Schemas for products/coproducts (case classes and case class families) can be traversed and modified using
`.modify` method. To traverse collections or options, use `.each`.

For example:

```scala
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.generic.Derived

case class Basket(fruits: List[FruitAmount])
case class FruitAmount(fruit: String, amount: Int)
implicit val customBasketSchema: Schema[Basket] = implicitly[Derived[Schema[Basket]]].value
  .modify(_.fruits.each.amount)(_.description("How many fruits?"))
```

There is also an unsafe variant of this method, but it should be avoided in most cases.
The "unsafe" prefix comes from the fact that the method takes a list of strings,
which represent fields, and the correctness of this specification is not checked.

Non-standard collections can be unwrapped in the modification path by providing an implicit value of `ModifyFunctor`.

### Using value classes/tagged types

An alternative to customising schemas for case class fields of primitive type (e.g. `Int`s), is creating a unique type.
As schema lookup is type-driven, if a schema for a such type is provided as an implicit value, it will be used 
during automatic or semi-automatic schema derivation. Such schemas can have custom meta-data, including description,
validation, etc.

To introduce unique types for primitive values, which don't have a runtime overhead, you can use value classes or 
[type tagging](https://github.com/softwaremill/scala-common#tagging).

For example, to support an integer wrapped in a value type in a json body, we need to provide Circe encoders and
decoders (if that's the json library that we are using), schema information with validator:

```scala
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.{ Encoder, Decoder }
import io.circe.generic.semiauto._

case class Amount(v: Int) extends AnyVal
case class FruitAmount(fruit: String, amount: Amount)

implicit val amountSchema: Schema[Amount] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
implicit val amountEncoder: Encoder[Amount] = Encoder.encodeInt.contramap(_.v)
implicit val amountDecoder: Decoder[Amount] = Decoder.decodeInt.map(Amount.apply)

implicit val decoder: Decoder[FruitAmount] = deriveDecoder[FruitAmount]
implicit val encoder: Encoder[FruitAmount] = deriveEncoder[FruitAmount]

val e: PublicEndpoint[FruitAmount, Unit, Unit, Nothing] =
  endpoint.in(jsonBody[FruitAmount])
```

## Next

Read on about [enumerations](enumerations.md).
