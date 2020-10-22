# Custom types

To support a custom type, you'll need to provide an implicit `Codec` for that type.

This can be done by writing a codec from scratch, mapping over an existing codec, or automatically deriving one.
Which of these approaches can be taken, depends on the context in which the codec will be used.

## Providing an implicit codec

To create a custom codec, you can either directly implement the `Codec` trait, which requires to provide the following
information:

* `encode` and `rawDecode` methods
* optional schema (for documentation)
* optional validator
* codec format (`text/plain`, `application/json` etc.)

This might be quite a lot of work, that's why it's usually easier to map over an existing codec. To do that, you'll 
need to provide two mappings: 

* a `decode` method which decodes the lower-level type into the custom type, optionally reporting decode failures 
(the return type is a `DecodeResult`)
* an `encode` method which encodes the custom type into the lower-level type

For example, to support a custom id type:

```scala
import scala.util._

class MyId private (id: String) {
  override def toString(): String = id
}
object MyId {
  def parse(id: String): Try[MyId] = {
    Success(new MyId(id))
  }
}
```

```scala
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

def decode(s: String): DecodeResult[MyId] = MyId.parse(s) match {
  case Success(v) => DecodeResult.Value(v)
  case Failure(f) => DecodeResult.Error(s, f)
}
def encode(id: MyId): String = id.toString

implicit val myIdCodec: Codec[String, MyId, TextPlain] = 
  Codec.string.mapDecode(decode)(encode)
```

Or, using the type alias for codecs in the TextPlain format and String as the raw value:

```scala
import sttp.tapir.Codec.PlainCodec

implicit val myIdCodec: PlainCodec[MyId] = Codec.string.mapDecode(decode)(encode)
```

```eval_rst
.. note::

  Note that inputs/outputs can also be mapped over. In some cases, it's enough to create an input/output corresponding 
  to one of the existing types, and then map over them. However, if you have a type that's used multiple times, it's 
  usually better to define a codec for that type. 
```

Then, you can use the new codec e.g. to obtain an id from a query parameter or a path segment:

```scala
endpoint.in(query[MyId]("myId"))
// or
endpoint.in(path[MyId])
```

## Automatically deriving codecs

In some cases, codecs can be automatically derived:

* for supported [json](json.md) libraries
* for urlencoded and multipart [forms](forms.md)

Automatic codec derivation usually requires other implicits, such as:

* json encoders/decoders from the json library
* codecs for individual form fields
* schema of the custom type, through the `Schema[T]` implicit

Note the derivation of e.g. circe json encoders/decoders and tapir schema are separate processes, and must be 
hence configured separately.

## Schema derivation

For case classes types, `Schema[_]` values can be derived automatically using [Magnolia](https://propensive.com/opensource/magnolia/), given
that schemas are defined for all the case class's fields. 

Two policies enable to choose the way custom types are derived : 
- Automatic derivation
- Semi automatic derivation 


### Automatic mode

Cases classes, traits and their children are recursively derived by Magnolia.   
 
Importing `sttp.tapir.generic.auto._` enables fully automatic derivation for `Schema` and `Validator`.

```scala
import sttp.tapir.Schema
import sttp.tapir.generic.auto._

case class Parent(child: Child)
case class Child(value: String)

// implicit schema used by codecs
implicitly[Schema[Parent]]

```

If you have a case class which contains some non-standard types (other than strings, number, other case classes, 
collections), you only need to provide schemas for them. Using these, the rest will be derived automatically.

### Semi-automatic mode

Semi-automatic derivation can be done using `Schema.derive[T]` or `Validator.derive[T]`. 

It only derives selected type `T`. However derivation is not recursive : 
schemas and validators must be explicitly defined for every child type.

This mode is easier to debug and helps to avoid issues encountered by automatic mode (wrong schemas for value classes or custom types).   

```scala
import sttp.tapir.Schema

case class Parent(child: Child)
case class Child(value: String)

implicit def sChild: Schema[Child] = Schema.derive
implicit def sParent: Schema[Parent] = Schema.derive

```

### Configuring derivation

It is possible to configure Magnolia's automatic derivation to use
snake_case, kebab-case or a custom field naming policy, by providing an implicit `sttp.tapir.generic.Configuration` value:

```scala
import sttp.tapir.generic.Configuration

implicit val customConfiguration: Configuration =
  Configuration.default.withSnakeCaseMemberNames
```

Alternatively, `Schema[_]` values can be defined by hand, either for whole case classes, or only for some of its fields.
For example, here we state that the schema for `MyCustomType` is a `String`:

```scala
import sttp.tapir._

case class MyCustomType()
implicit val schemaForMyCustomType: Schema[MyCustomType] = Schema(SchemaType.SString)
```

### Sealed traits / coproducts

Schema derivation for coproduct types (sealed trait hierarchies) is supported as well. By default, such hierarchies
will be represented as a coproduct which contains a list of child schemas, without any discriminator field.

A discriminator field can be specified for coproducts by providing it in the configuration; this will be only used
during automatic derivation:

```scala
import sttp.tapir.generic.Configuration

implicit val customConfiguration: Configuration =
  Configuration.default.withDiscriminator("who_am_i")
```

Alternatively, derived schemas can be customised (see below), and a discriminator can be added by calling
the `SchemaType.SCoproduct.addDiscriminatorField(name, schema, mapingOverride)` method.

Finally, if the discriminator is a field that's defined on the base trait (and hence in each implementation), the
schemas can be specified using `Schema.oneOfUsingField`, for example (this will also generate the appropriate
mapping overrides):

```scala
sealed trait Entity {
  def kind: String
} 
case class Person(firstName:String, lastName:String) extends Entity { 
  def kind: String = "person"
}
case class Organization(name: String) extends Entity {
  def kind: String = "org"  
}

import sttp.tapir._

val sPerson = Schema.derive[Person]
val sOrganization = Schema.derive[Organization]
implicit val sEntity: Schema[Entity] = 
    Schema.oneOfUsingField[Entity, String](_.kind, _.toString)("person" -> sPerson, "org" -> sOrganization)
```

## Customising derived schemas

In some cases, it might be desirable to customise the derived schemas, e.g. to add a description to a particular
field of a case class. One way the automatic derivation can be customized is using annotations:

* `@encodedName` sets name for case class's field which is used in the encoded form (and also in documentation)
* `@description` sets description for the whole case class or its field
* `@format` sets the format for a case class field
* `@deprecated` marks a case class's field as deprecated

If the target type isn't accessible or can't be modified, schemas can be customized by looking up an implicit instance 
of the `Derived[Schema[T]]` type, modyfing the value, and assigning it to an implicit schema. 

When such an implicit `Schema[T]` is in scope will have higher priority than the built-in low-priority conversion 
from `Derived[Schema[T]]` to `Schema[T]`.

Schemas for products/coproducts (case classes and case class families) can be traversed and modified using
`.modify` method. To traverse collections, use `.each`.

For example:

```scala
import sttp.tapir._
import sttp.tapir.generic.auto.schema._
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

## Next

Read on about [validation](validation.md).
