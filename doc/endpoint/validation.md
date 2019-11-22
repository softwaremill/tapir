# Validation

Tapir supports validation for primitive/base types. Validation of composite values, whole data structures, business 
rules enforcement etc. should be done as part of the [server logic](../server/logic.html) of the endpoint, using the 
dedicated error output (the `E` in `Endpoint[I, E, O, S]`) to report errors. 

## Single type validation

Validation rules are part of the [codec](codecs.html) for a given type. They can be specified when creating the codec
(using the `Codec.validate()` method):
 
```scala
case class MyId(id: String)

implicit val myIdCodec: Codec[MyId, TextPlain, _] = Codec.stringPlainCodecUtf8
  .mapDecode(decode)(encode)
  .validate(Validator.pattern("^[A-Z].*").contramap(_.id))
```
 
Or added to individual inputs/outputs:

```scala
val e = endpoint.in(
  query[Int]("amount")
    .validate(Validator.min(0))
    .validate(Validator.max(100)))
``` 

Validation rules added using the built-in validators are translated to [OpenAPI](../openapi.html) documentation.

### Validation rules and automatic codec derivation

When a codec is automatically derived for a type (see [custom types](customtypes.md)), validators for all types 
(for json this is a recursive process) are looked up through implicit `Validator[T]` values.

> Implicit `Validator[T]` values are used *only* when automatically deriving codecs. They are not used
> when the codec is defined by hand.

Note that to validate a nested member of a case class, it needs to have a unique type (that is, not an `Int`, as 
providing an implicit `Validator[Int]` would validate all ints in the hierarchy), as validator lookup is type-driven.

To introduce unique types for primitive values, you can use value classes or [type tagging](https://github.com/softwaremill/scala-common#tagging).

For example, to support an integer wrapped in a value type in a json body, we need to provide Circe encoders and 
decoders (if that's the json library that we are using), schema information and a validator:
 
```scala
case class Amount(v: Int) extends AnyVal
case class FruitAmount(fruit: String, amount: Amount)

val e: Endpoint[FruitAmount, Unit, Unit, Nothing] = {
  implicit val schemaForAmount: SchemaFor[Amount] = SchemaFor(Schema.SInteger)
  implicit val encoder: Encoder[Amount] = Encoder.encodeInt.contramap(_.v)
  implicit val decode: Decoder[Amount] = Decoder.decodeInt.map(Amount.apply)
  implicit val v: Validator[Amount] = Validator.min(1).contramap(_.v)
  endpoint.in(jsonBody[FruitAmount])
}
```

## Decode failures

Codecs support reporting decoding failures, by returning a `DecodeResult` from the `Codec.decode` method. However, this 
is meant for input/output values which are in an incorrect low-level format, when parsing a "raw value" fails. In other 
words, decoding failures should be reported for format failures, not business validation errors.
 
Decoding failures should be reported when the input is in an incorrect low-level format, when parsing a "raw value"
fails. In other words, decoding failures should be reported for format failures, not business validation errors.

To customise error messages that are returned upon validation/decode failures by the server, see 
[error handling](../server/errors.html).

## Enum validators

Validators for enumerations can be created using the `Validator.enum` method, which either:

* takes a type parameter, which should be an abstract, sealed base type, and using a macro determines the possible 
  implementations
* takes the list of possible values

To properly represent possible values in documentation, the enum validator additionally needs an `encode` method, which 
converts the enum value to a raw type (typically a string). This method is inferred *only* if the validator is directly 
added to a codec (without any mapping etc.), for example:

```scala
sealed trait Color
case object Blue extends Color
case object Red extends Color

implicit def plainCodecForColor: PlainCodec[Color] = {
  Codec.stringPlainCodecUtf8
    .map[Color]({
      case "red"  => Red
      case "blue" => Blue
    })(_.toString.toLowerCase)
    .validate(Validator.enum)
}
```

If the enum is nested within an object, regardless of whether the codec for that object is defined by hand or derived,
we need to specify the encode function by hand:

```scala
implicit def colorValidator: Validator[Color] = Validator.enum.encode(_.toString.toLowerCase)
```

Like other validators, enum validators need to be added to a codec, or through an implicit value, if the codec and
validator is automatically derived. 

## Next

Read on about [json support](json.html).
