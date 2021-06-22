# Validation

Tapir supports validation for primitive types. Validation of composite values, whole data structures, business 
rules enforcement etc. should be done as part of the [server logic](../server/logic.md) of the endpoint, using the 
dedicated error output (the `E` in `Endpoint[I, E, O, S]`) to report errors. 

## Single type validation

Validation rules are part of the [`Schema`](codecs.md#schemas) for a given type, and can be added either directly
to the schema, or via the `Codec` or `EndpointInput`/`EndpointOutput`. For example, when defining a codec for a type, 
we have the `.validate()` method:
 
```scala
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

case class MyId(id: String)

implicit val myIdCodec: Codec[String, MyId, TextPlain] = Codec.string
  .map(MyId(_))(_.id)
  .validate(Validator.pattern("^[A-Z].*").contramap(_.id))
```
 
Validators can also be added to individual inputs/outputs, in addition to whatever the codec provides:

```scala
import sttp.tapir._

val e = endpoint.in(
  query[Int]("amount")
    .validate(Validator.min(0))
    .validate(Validator.max(100)))
``` 

For optional/iterable inputs/outputs, to validate the contained value(s), use:

```scala
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

* `Validator.derivedEnum`, which takes a type parameter. This should be an abstract, sealed base type, and using a 
  macro determines the possible values
* `Validator.enum`, which takes the list of possible values

To properly represent possible values in documentation, the enum validator additionally needs an `encode` method, which 
converts the enum value to a raw type (typically a string). This method is inferred *only* if the validator is directly 
added to a codec (without any mapping etc.), for example:

```scala
import sttp.tapir._
import sttp.tapir.Codec.PlainCodec

sealed trait Color
case object Blue extends Color
case object Red extends Color

implicit def plainCodecForColor: PlainCodec[Color] = {
  Codec.string
    .map[Color]((_: String) match {
      case "red"  => Red
      case "blue" => Blue
    })(_.toString.toLowerCase)
    .validate(Validator.derivedEnumeration)
}
```

If the enum is nested within an object, regardless of whether the codec for that object is defined by hand or derived,
we need to specify the encode function by hand:

```scala
implicit def colorSchema: Schema[Color] = Schema.string.validate(Validator.derivedEnumeration.encode(_.toString.toLowerCase))
```

Like other validators/schemas, enum schemas need to be added to a codec manually, or through an implicit value if the 
codec is automatically derived. 

## Next

Read on about [content types](contenttype.md).
