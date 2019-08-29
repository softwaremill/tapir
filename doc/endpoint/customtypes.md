# Custom types

To support a custom type, you'll need to provide an implicit `Codec` for that type.

This can be done by writing a codec from scratch, mapping over an existing codec, or automatically deriving one.
Which of these approaches can be taken, depends on the context in which the codec will be used.

## Providing an implicit codec

To create a custom codec, you can either directly implement the `Codec` trait, which requires to provide the following
information:

* `encode` and `decode` methods
* codec meta-data (`CodecMeta`) consisting of:
** schema of the type (for documentation)
** validator for the type
** media type (`text/plain`, `application/json` etc.)
** type of the raw value, to which data is serialised (`String`, `Int` etc.)

This might be quite a lot of work, that's why it's usually easier to map over an existing codec. To do that, you'll 
need to provide two mappings: 

* an `encode` method which encodes the custom type into the base type
* a `decode` method which decodes the base type into the custom type, optionally reporting decode errors (the return
type is a `DecodeResult`)

For example, to support a custom id type:

```scala
def decode(s: String): DecodeResult[MyId] = MyId.parse(s) match {
  case Success(v) => DecodeResult.Value(v)
  case Failure(f) => DecodeResult.Error(s, f)
}
def encode(id: MyId): String = id.toString

implicit val myIdCodec: Codec[MyId, TextPlain, _] = Codec.stringPlainCodecUtf8
  .mapDecode(decode)(encode)
```

> Note that inputs/outputs can also be mapped over. However, this kind of mapping is always an isomorphism, doesn't
> allow any validation or reporting decode errors. Hence, it should be used only for grouping inputs or outputs
> from a tuple into a custom type.

## Automatically deriving codecs

In some cases, codecs can be automatically derived:

* for supported [json](json.html) libraries
* for urlencoded and multipart [forms](forms.html)

Automatic codec derivation usually requires other implicits, such as:

* json encoders/decoders from the json library
* codecs for individual form fields
* schema of the custom type, through the `SchemaFor[T]` implicit

`SchemaFor[_]` implicit values are also automatically derived for case classes. You might need to provide implicit
`SchemaFor[_]` values for any nested custom types, that occur in the case classes, for derivation to work properly.

## Next

Read on about [validation](validation.html).
