# Validation

Tapir supports validation for primitive types. Validation of composite values, whole data structures, business 
rules enforcement etc. should be done as part of the [server logic](../server/logic.md) of the endpoint, using the 
dedicated error output (the `E` in `Endpoint[A, I, E, O, S]`) to report errors.

For some guidelines as to where to perform a specific type of validation, see the ["Validation analysis paralysis"](https://blog.softwaremill.com/validation-analysis-paralysis-ca9bdef0a6d7) article. A good indicator as to where to place particular validation
logic might be if the property that we are checking is a format error, or a business-level error? The validation
capabilities described in this section are intended only for format errors.

## Single type validation

Validation rules are part of the [`Schema`](codecs.md#schemas) for a given type, and can be added either directly
to the schema, or via the `Codec` or `EndpointInput`/`EndpointOutput`. For example, when defining a codec for a type, 
we have the `.validate()` method:
 
```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

case class MyId(id: String)

implicit val myIdCodec: Codec[String, MyId, TextPlain] = Codec.string
  .map(MyId(_))(_.id)
  .validate(Validator.pattern("^[A-Z].*").contramap(_.id))
```
 
Validators can also be added to individual inputs/outputs, in addition to whatever the codec provides:

```scala mdoc:compile-only
import sttp.tapir._

val e = endpoint.in(
  query[Int]("amount")
    .validate(Validator.min(0))
    .validate(Validator.max(100)))
``` 

For optional/iterable inputs/outputs, to validate the contained value(s), use:

```scala mdoc:compile-only
import sttp.tapir._

query[Option[Int]]("item").validateOption(Validator.min(0))
query[List[Int]]("item").validateIterable(Validator.min(0)) // validates each repeated parameter
```

Validation rules added using the built-in validators are translated to [OpenAPI](../docs/openapi.md) documentation.

## Validation rules and automatic codec derivation

As validators are parts of schemas, they are looked up as part of the implicit `Schema[T]` values. When 
[customising schemas](schemas.md), use the `.validate` method on the schema to add a validator.

## Decode failures

Codecs support reporting decoding failures, by returning a `DecodeResult` from the `Codec.decode` method. However, this 
is meant for input/output values which are in an incorrect low-level format, when parsing a "raw value" fails. In other 
words, decoding failures should be reported for format failures, not business validation errors.

To customise error messages that are returned upon validation/decode failures by the server, see 
[error handling](../server/errors.md).

## Enum validators

Validators for enumerations can be created using:

* `Validator.derivedEnumeration`, which takes a type parameter. This should be an abstract, sealed base type, and using a 
  macro determines the possible values
* `Validator.enumeration`, which takes the list of possible values

To properly represent possible values in documentation, the enum validator additionally needs an `encode` method, which 
converts the enum value to a raw type (typically a string). This can be specified by:

* explicitly providing it using the overloaded `enumeration` method with an `encode` parameter
* by using one of the `.encode` methods on the `Validator.Enumeration` instance
* when the values possible values are of a basic type (numbers, strings), the encode function is inferred if not present
* by adding the validator directly to a codec using `.validate` (the encode function is then taken from the codec)

To simplify creation of schemas and codec, with a derived enum validator, `Schema.derivedEnumeration` and `Codec.derivedEnumeration`
helper methods are available. For example:

```scala mdoc:silent:reset-object
import sttp.tapir._
import sttp.tapir.Codec.PlainCodec

sealed trait Color
case object Blue extends Color
case object Red extends Color

implicit def plainCodecForColor: PlainCodec[Color] = {
  Codec.derivedEnumeration[String, Color](
    (_: String) match {
      case "red"  => Some(Red)
      case "blue" => Some(Blue)
      case _      => None
    },
    _.toString.toLowerCase
  )
}
```

If the enum is nested within an object and its values aren't of a "basic" type (numbers, strings), regardless of whether 
the codec for that object is defined by hand or derived, we need to specify the encode function by hand:

```scala mdoc:silent
// providing the enum values by hand
implicit def colorSchema: Schema[Color] = Schema.string.validate(
  Validator.enumeration(List(Blue, Red), (c: Color) => Some(c.toString.toLowerCase)))

// or deriving the enum values and using the helper function
implicit def colorSchema2: Schema[Color] = Schema.derivedEnumeration[Color](encode = Some(_.toString.toLowerCase))
```

## Next

Read on about [content types](contenttype.md).
