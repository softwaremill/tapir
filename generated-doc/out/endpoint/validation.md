# Validation

Tapir supports validation for primitive/base types. Validation of composite values, whole data structures, business 
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

Validation rules added using the built-in validators are translated to [OpenAPI](../openapi.md) documentation.

## Validation rules and automatic codec derivation

As validators are parts of schemas, they are looked up as part of the with implicit `Schema[T]` values. 

While they can be manually defined, tapir provides tools to derive automatically schemas for custom types 
(traits and case classes).

## Custom type validation

Note that to validate a nested member of a case class, it needs to have a unique type (that is, not an `Int`, as 
providing an implicit `Validator[Int]` would validate all ints in the hierarchy), as schema lookup is type-driven.

To introduce unique types for primitive values, you can use value classes or [type tagging](https://github.com/softwaremill/scala-common#tagging).

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

implicit val amountSchema: Schema[Amount] = Schema(SchemaType.SInteger).validate(Validator.min(1).contramap(_.v))
implicit val amountEncoder: Encoder[Amount] = Encoder.encodeInt.contramap(_.v)
implicit val amountDecoder: Decoder[Amount] = Decoder.decodeInt.map(Amount.apply)

implicit val decoder: Decoder[FruitAmount] = deriveDecoder[FruitAmount]
implicit val encoder: Encoder[FruitAmount] = deriveEncoder[FruitAmount]

val e: Endpoint[FruitAmount, Unit, Unit, Nothing] =
  endpoint.in(jsonBody[FruitAmount])
```

## Decode failures

Codecs support reporting decoding failures, by returning a `DecodeResult` from the `Codec.decode` method. However, this 
is meant for input/output values which are in an incorrect low-level format, when parsing a "raw value" fails. In other 
words, decoding failures should be reported for format failures, not business validation errors.

To customise error messages that are returned upon validation/decode failures by the server, see 
[error handling](../server/errors.md).

## Enum validators

Validators for enumerations can be created using the `Validator.enum` method, which either:

* takes a type parameter, which should be an abstract, sealed base type, and using a macro determines the possible 
  implementations
* takes the list of possible values

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
    .validate(Validator.enum)
}
```

If the enum is nested within an object, regardless of whether the codec for that object is defined by hand or derived,
we need to specify the encode function by hand:

```scala
implicit def colorSchema: Schema[Color] = Schema.string.validate(Validator.enum.encode(_.toString.toLowerCase))
```

Like other validators/schemas, enum schemas need to be added to a codec manually or through an implicit value, if the 
codec and validator is automatically derived. 

## Next

Read on about [json support](json.md).
