# Enumerations

tapir supports both `scala.Enumeration`-based enumerations, as well as enumerations created as a `sealed` family of
`object`s (in Scala 2) or Scala 3 `enum`s where all cases are parameterless. Other enumeration implementations are
also supported by integrating with [third-party libraries](integrations.md).

Depending on the context, in which an enumeration is used, you'll need to create either a [`Schema`](schemas.md), or
a [`Codec`](codecs.md) (which includes a schema).

## Using enumerations as values of query parameters, headers, path components

When using an enumeration in such a context, a `Codec` has to be defined for the enumeration.

tapir needs to know how to decode a low-level value into the enumeration, and how to encode an enumeration value into 
the low-level representation. This is handled by the codec's `decode` and `encode` functions.

Moreover, each codec is associated with a schema (which describes the low-level representation for documentation).
A schema, in turn, can have associated validators. In case of enumerations, a `Validator.Enumeration` should be added. 
The validator contains a list of all possible values (which are used when generating the docs).

The enumeration validator doesn't provide any important run-time behavior, as if a value can be represented as an 
enumeration in the first place (by the process of decoding), it is valid. However, the codec's `decode` should return a
`DecodeResult.InvalidValue` with a reference to the validator, if validation fails. This way, the 
[server](../server/errors.md) can provide appropriate user-friendly messages.

### scala.Enumeration support

A default codec for any subtype of `scala.Enumeration#Value` is provided as an implicit/given value. Such a codec
assumes that the low-level representation of the enumeration is a string. Encoding is done using `.toString`, while
decoding performs a case-insensitive search through the enumeration's values. For example:

```scala 
import sttp.tapir.*

object Features extends Enumeration:
  type Feature = Value

  val A: Feature = Value("a")
  val B: Feature = Value("b")
  val C: Feature = Value("c")

query[Features.Feature]("feature")
```

This can be customised (e.g. if the encoding/decoding should behave differently, or if the low-level representation
should be a number), by defining an implicit codec:

```scala
import sttp.tapir.Codec.PlainCodec

implicit val customFeatureCodec: PlainCodec[Features.Feature] = 
  Codec.derivedEnumerationValueCustomise[Int, Features.Feature](
    {
      case 0 => Some(Features.A)
      case 1 => Some(Features.B)
      case 2 => Some(Features.C)
      case _ => None
    },
    {
      case Features.A => 0
      case Features.B => 1
      case Features.C => 2
      case _         => -1
    },
    None
  )
```

### Sealed families / enum support

When the enumeration is defined as a sealed family containing only objects, or a Scala 3 `enum` with all cases
parameterless, a codec has to be provided as an implicit value by hand. 

There is no implicit/given codec provided by default, as there's no way to constrain the type for which such an implicit
would be considered by the compiler.

For example:

```scala mdoc:silent:reset-object
import sttp.tapir.*
import sttp.tapir.Codec.PlainCodec

sealed trait Feature
object Feature:
  case object A extends Feature
  case object B extends Feature
  case object C extends Feature

given PlainCodec[Feature] = 
  Codec.derivedEnumeration[String, Feature].defaultStringBased

query[Feature]("feature")
```

The `.defaultStringBased` method creates a default codec with decoding and encoding rules as described for the
default `Enumeration` codec (using `.toString`). Such a codec can be similarly customised, by providing the `encode`
and `decode` functions as parameters to the value returned to `derivedEnumeration`:

```scala mdoc:silent:reset-object
import sttp.tapir.*
import sttp.tapir.Codec.PlainCodec

sealed trait Color
case object Blue extends Color
case object Red extends Color

given PlainCodec[Color] =
  Codec.derivedEnumeration[String, Color](
    (_: String) match {
      case "red"  => Some(Red)
      case "blue" => Some(Blue)
      case _      => None
    },
    _.toString.toLowerCase
  )
```

### Creating an enum codec by hand

Creating an enumeration [codec](codecs.md) by hand is exactly the same as for any other type. The only difference
is that an enumeration [validator](validation.md) has to be added to the codec's schema. Note that when decoding a
value fails, it's best to return a `DecodeResult.InvalidValue`, with a reference to the enumeration validator.

### Lists of enumeration values

If an input/output contains multiple enumeration values, delimited e.g. using a comma, you can look up a codec for 
`CommaSeparated[T]` or `Delimited[DELIMITER, T]` (where `D` is a type literal). The `Delimited`
type is a simple wrapper for a list of `T`-values. For example, if the query parameter is required:

```scala mdoc:silent
import sttp.tapir.*
import sttp.tapir.model.CommaSeparated

object Features extends Enumeration:
  type Feature = Value

  val A: Feature = Value("a")
  val B: Feature = Value("b")
  val C: Feature = Value("c")

query[CommaSeparated[Features.Feature]]("features")
```

Additionally, the schema for such an input/output will have the `explode` parameter set to `false`, so that it is
properly represented in [OpenAPI](../docs/openapi.md) documentation.

You can take a look at a runnable example [here](https://github.com/softwaremill/tapir/tree/master/examples/src/main/scala/sttp/tapir/examples/custom_types).

```{warning}
`Delimited` and `CommaSeparated` rely on literal types, which are only available in Scala 2.13+.
  
If you're using an older version of Scala, a workaround is creating a comma-separated codec locally.
```

## Using enumerations as part of bodies

When an enumeration is used as part of a body, on the tapir side you'll have to provide a [schema](schemas.md) for
that type, so that the documentation is properly generated.

Note, however, that the enumeration will also need to be properly supported by whatever means that body is parsed.
If we have an [JSON](json.md) body, parsed with circe, you'll also need to provide circe's `Encoder` and `Decoder`
implicits for the enumerations type, for the parsing to work properly.

### scala.Enumeration support

A default schema for any subtype of `scala.Enumeration#Value` is provided as an implicit/given value. Such a schema
assumes that the low-level representation of the enumeration is a string. Encoding is done using `.toString` (to
represent the enumeration's values in the documentation). For example, to use an enum as part of a `jsonBody`, using
the circe library for JSON parsing/serialisation, and automatic schema derivation for case classes:

```scala
import io.circe.*
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.* 

object Features extends Enumeration:
  type Feature = Value

  val A: Feature = Value("a")
  val B: Feature = Value("b")
  val C: Feature = Value("c")

case class Body(someField: String, feature: Features.Feature)

// these need to be provided so that circe knows how to encode/decode enumerations - will work only in Scala2!
given Decoder[Features.Feature] = Decoder.decodeEnumeration(Features)
given Encoder[Features.Feature] = Encoder.encodeEnumeration(Features)

// the schema for the body is automatically-derived, using the default schema for 
// enumerations (Schema.derivedEnumerationValue)
jsonBody[Body]
``` 

A custom schema can be created by providing an alternate schema type (e.g. if the low-level representation of the
enumeration is an integer), using `Schema.derivedEnumerationValueCustomise.apply(...)`. In this case, you'll need
to provide the schema an implicit/given value:

```scala mdoc:silent:reset-object
import sttp.tapir.*

object Features extends Enumeration:
  type Feature = Value

  val A: Feature = Value("a")
  val B: Feature = Value("b")
  val C: Feature = Value("c")

given Schema[Features.Feature] = 
  Schema.derivedEnumerationValueCustomise[Features.Feature](
    encode = Some {
      case Features.A => 0
      case Features.B => 1
      case Features.C => 2
      case _         => -1
    },
    schemaType = SchemaType.SInteger()
  )
```

### Sealed families / Scala3 enum support

When the enumeration is defined as a sealed family containing only objects, or a Scala 3 `enum` with all cases
parameterless, a schema has to be provided as an implicit/given value.

There is no implicit/given schema provided by default, as there's no way to constrain the type for which such an 
implicit would be considered by the compiler. Moreover, when automatic [schema](schemas.md) derivation is used,
the current implementation has no possibility to create the list of possible enumeration values (which is needed
to create the enumeration validator). This might be changed in the future, but currently schemas for enumerations
need to be created using `.derivedEnumeration`, instead of the more general `.derived`.

For example:

```scala mdoc:silent:reset-object
import sttp.tapir.*

sealed trait Feature
object Feature:
  case object A extends Feature
  case object B extends Feature
  case object C extends Feature

given Schema[Feature] = 
  Schema.derivedEnumeration[Feature].defaultStringBased
```

Similarly, using Scala 3's enums:

```scala
enum ColorEnum:
  case Green extends ColorEnum
  case Pink extends ColorEnum

given Schema.derivedEnumeration.defaultStringBased
```

### Scala 3 string-based constant union types to enum

If a union type is a string-based constant union type, it can be auto-derived as field type or manually derived by using the `Schema.derivedStringBasedUnionEnumeration[T]` method.

Constant strings can be derived by using the `Schema.constStringToEnum[T]` method.

Examples:
```scala
val aOrB: Schema["a" | "b"] = Schema.derivedStringBasedUnionEnumeration
```
```scala
val a: Schema["a"] = Schema.constStringToEnum
```
```scala
case class Foo(aOrB: "a" | "b", optA: Option["a"]) derives Schema
```

### Creating an enum schema by hand

Creating an enumeration [schema](schemas.md) by hand is exactly the same as for any other type. The only difference
is that an enumeration [validator](validation.md) has to be added to the schema.

## Next

Read on about [validation](validation.md).
