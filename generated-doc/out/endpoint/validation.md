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

```scala
import sttp.tapir.*

val e = endpoint.in(
  query[Int]("amount")
    .validate(Validator.min(0))
    .validate(Validator.max(100)))
``` 

For optional/iterable inputs/outputs, to validate the contained value(s), use:

```scala
import sttp.tapir.*

query[Option[Int]]("item").validateOption(Validator.min(0))
query[List[Int]]("item").validateIterable(Validator.min(0)) // validates each repeated parameter
```

## Adding validators to codecs

Finally, if you are creating a reusable [codec](codecs.md), a validator can be added to it as well:

```scala
import sttp.tapir.*
import sttp.tapir.CodecFormat.TextPlain

case class MyId(id: String)

given Codec[String, MyId, TextPlain] = Codec.string
  .map(MyId(_))(_.id)
  .validate(Validator.pattern("^[A-Z].*").contramap(_.id))
```

## Decode failures

The validators are run when a value is being decoded from its low-level representation. This is done using the
`Codec.decode` method, which returns a `DecodeResult`. Such a result can be successful, or a decoding failure.

Keep in mind that the validator mechanism described here is meant for input/output values which are in an incorrect 
low-level format. Validation and more generally decoding failures should be reported only for format failures.
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

The enumeration schemas and codecs that are created by tapir-provided methods already have the enumeration validator
added. See the section on [enumerations](enumerations.md) for more information. 

### Enumeration values in documentation

To properly represent possible values in documentation, the enum validator additionally needs an `encode` method, which
converts the enum value to a raw type (typically a string). This can be specified by:

* explicitly providing it using the overloaded `enumeration` method with an `encode` parameter
* by using one of the `.encode` methods on the `Validator.Enumeration` instance
* when the values possible values are of a basic type (numbers, strings), the encode function is inferred if not present
* by adding the validator directly to a codec using `.validate` (the encode function is then taken from the codec)

For example:

```scala
import sttp.tapir.*

sealed trait Color
case object Blue extends Color
case object Red extends Color

// providing the enum values by hand
given Schema[Color] = Schema.string.validate(
  Validator.enumeration(List(Blue, Red), (c: Color) => Some(c.toString.toLowerCase)))
```

## Validation of unrepresentable values

Note that validation is run on a fully decoded values. That is, during decoding, first all the provided decoding 
functions are run, followed by validations. If you'd like to validate before decoding, e.g. because the value 
isn't representable unless validator conditions are met due to preconditions, you can use ``.mapValidate``. However,
this will cause the validator function to be run twice if there are no validation error.

## Next

Read on about [content types](contenttype.md).
