# Implicits guide for custom types

A `could not find implicit value` error can be sometimes puzzling, so here's a short summary of what kind of implicits
tapir uses for supporting custom types.

In general, when using a custom type in any context, an implicit `Codec[T, _, _]` is required. Codecs for custom types
can be either derived automatically, or created basing on existing codecs.

## Path, query parameters and headers

When using a custom type for a path parameter, query parameter or header value, you'll need a codec with the
`text/plain` media type. You can use an existing codec and map over it, to create a new one. For example:

```scala
case class MyId(...)
object MyId {
  def parse(s: String): Try[String] = ...
}

def decode(s: String): DecodeResult[MyId] = MyId.parse(s) match {
  case Success(v) => DecodeResult.Value(v)
  case Failure(f) => DecodeResult.Error(s, f)
}
def encode(id: MyId): String = id.toString

implicit val myIdCodec: Codec[MyId, TextPlain, _] = Codec.stringPlainCodecUtf8.mapDecode(decode)(encode)
```

## Text and binary bodies

The approach for text and binary bodies is the same as for queries/paths/headers. To support a custom types, you'll
need to map over an existing codec, for example `Codec.byteArrayCodec` or `Codec.stringPlainCodecUtf8`, and assign]
the result to an implicit value.

## JSON bodies

When working with json bodies, the custom types can be much more complex than when mapping a query or path parameter.
Using the circe integration, a `Codec[T, Json, _]`, where `T` is a `case class`, can be automatically derived given the
following implicit values:

* `io.circe.Encoder[T]`
* `io.circe.Decoder[T]`
* `tapir.SchemaFor[T]`

The circe encoders/decoders have to be provided using one of the methods supported by Circe, e.g. by importing
`import io.circe.generic.auto._`.

The `SchemaFor[T]` can be auto-generated using Magnolia, or provided by hand. See [json](json.html) for more details.

> In the future, it would be ideal if encoders/decoders could be derived automatically from the schema. For now however,
the schema and the json encoders have to be provided separately.

## Form bodies

When mapping either url-encoded or multipart [form bodies](forms.html), for each field, a plain codec has to be available
in the implicit scope. That is, a value of type `Codec[R, TextPlain, _]`, for each `R` which is a field of the case
class to which the data is being mapped.