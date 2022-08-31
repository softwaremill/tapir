# Validation

Tapir supports validation for primitive types. Validation of composite values, whole data structures, business 
rules enforcement etc. should be done as part of the [server logic](../server/logic.md) of the endpoint, using the 
dedicated error output (the `E` in `Endpoint[A, I, E, O, S]`) to report errors.

For some guidelines as to where to perform a specific type of validation, see the ["Validation analysis paralysis"](https://blog.softwaremill.com/validation-analysis-paralysis-ca9bdef0a6d7) article. A good indicator as to where to place particular validation
logic might be if the property that we are checking is a format error, or a business-level error? The validation
capabilities described in this section are intended only for format errors.

Validation rules added using the built-in validators are translated to [OpenAPI](../docs/openapi.md) documentation.

## Adding validators to schemas

A validator is always part of a `Schema`, which is part of a `Codec`. It can validate either the top-level object, or 
some nested component (such as a field value). 

If you are using automatic or semi-automatic schema derivation, validators for such schemas, and their nested 
components, including collections and options, can be added as described in 
[schema customisation](schemas.md#customising-derived-schemas).

## Adding validators to inputs/outputs

Validators can also be added to individual inputs/outputs. Behind the scenes, this modifies the schema, but it's easier
to add top-level validators this way, rather than modifying the implicit schemas, for example:

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

## Adding validators to codecs

Finally, if you are creating a reusable [codec](codecs.md), a validator can be added to it as well:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.CodecFormat.TextPlain

case class MyId(id: String)

implicit val myIdCodec: Codec[String, MyId, TextPlain] = Codec.string
  .map(MyId(_))(_.id)
  .validate(Validator.pattern("^[A-Z].*").contramap(_.id))
```

## Decode failures

The validators are run when a value is being decoded from its low-level representation. This is done using the
`Codec.decode` method, which returns a `DecodeResult`. Such a result can be successful, or a decoding failure.

Keep in mind, that the validator mechanism described here is meant for input/output values which are in an incorrect 
low-level format. Validators and more generally decoding failures should be reported only for format failures.
Business validation errors, which are often contextual, should use the error output instead.

To customise error messages that are returned upon validation/decode failures by the server, see 
[error handling](../server/errors.md).

## Enumeration validators

Validators for enumerations can be created using:

* for arbitrary types, using `Validator.enumeration`, which takes the list of possible values
* for `sealed` hierarchies, where all implementations are objects, using `Validator.derivedEnumeration[T]`. 
  This method is a macro which determines the possible values.
* for Scala3 `enum`s, where all implementation don't have parameters, using `Validator.derivedEnumeration[T]` as above
* for Scala2 `Enumeration#Value`, automatically derived `Schema`s have the validator added (see `Schema.derivedEnumerationValue`)

### Enumeration values in documentation

To properly represent possible values in documentation, the enum validator additionally needs an `encode` method, which 
converts the enum value to a raw type (typically a string). This can be specified by:

* explicitly providing it using the overloaded `enumeration` method with an `encode` parameter
* by using one of the `.encode` methods on the `Validator.Enumeration` instance
* when the values possible values are of a basic type (numbers, strings), the encode function is inferred if not present
* by adding the validator directly to a codec using `.validate` (the encode function is then taken from the codec)

### Enumerations in schemas/codecs

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

If the enum values aren't of a "basic" type (numbers, strings), regardless of whether the codec for that object is 
defined by hand or derived, we need to specify the encode function by hand:

```scala mdoc:silent
// providing the enum values by hand
implicit def colorSchema: Schema[Color] = Schema.string.validate(
  Validator.enumeration(List(Blue, Red), (c: Color) => Some(c.toString.toLowerCase)))

// or deriving the enum values and using the helper function
implicit def colorSchema2: Schema[Color] = Schema.derivedEnumeration[Color](encode = Some(_.toString.toLowerCase))
```

### Scala3 enums

Due to technical limitations, automatically derived schemas for `enum`s where all cases are parameterless don't have
the enumeration validator added. Until this limitation is lifted, you'll have to define `implicit` (or equivalently, 
`given`) schemas in such cases by hand. These values will be used when deriving schemas containing your enumeration:

```scala
enum ColorEnum {
  case Green extends ColorEnum
  case Pink extends ColorEnum
}

given Schema[ColorEnum] = Schema.derivedEnumeration(encode = Some(v => v))
```

## Next

Read on about [content types](contenttype.md).
