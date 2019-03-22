# Codecs

A codec specifies how to map from and to raw values that are sent over the network. Raw values, which are natively 
supported by client/server interpreters, include `String`s, byte arrays, `File`s and multiparts.

There are built-in codecs for most common types such as `String`, `Int` etc. Codecs are usually defined as implicit 
values and resolved implicitly when they are referenced.

For example, a `query[Int]("quantity")` specifies an input parameter which corresponds to the `quantity` query 
parameter and will be mapped as an `Int`. There's an implicit `Codec[Int]` value that is referenced by the `query`
method (which is defined in the `tapir` package). 

In a server setting, if the value cannot be parsed as an int, a decoding failure is reported, and the endpoint 
won't match the request, or a `400 Bad Request` response is returned (depending on configuration).

## Optional and multiple parameters

Some inputs/outputs allow optional, or multiple parameters:

* path segments are always required
* query and header values can be optional or multiple (repeated query parameters/headers)
* bodies can be optional, but not multiple

In general, optional parameters are represented as `Option` values, and multiple parameters as `List` values.
For example, `header[Option[String]]("X-Auth-Token")` describes an optional header. An input described as 
`query[List[String]]("color")` allows multiple occurences of the `color` query parameter, with all values gathered
into a list.

### Implementation note

To support optional and multiple parameters, inputs/outputs don't require implicit `Codec` values (which represent
only mandatory values), but `CodecForOptional` and `CodecForMany` implicit values.

A `CodecForOptional` can be used in a context which *allows* optional values. Given a `Codec[T]`, instances of both 
`CodecForOptional[T]` and `CodecForOptional[Option[T]]` will be generated (that's also the way to add support for 
custom optional types). The first one will require a value, and report a decoding failure if a value is missing. The
second will properly map to an `Option`, depending if the value is present or not.

## Schemas

A codec also contains the schema of the mapped type. This schema information is used when generating documentation. 
For primitive types, the schema values are built-in, and include values such as `Schema.SString`, `Schema.SArray`, 
`Schema.SBinary` etc. 

For complex types, it is possible to define the schema by hand and apply it to a codec (using the `codec.schema` 
method), however usually the schema is looked up by codecs by requiring an implicit value of type
`SchemaFor[T]`. A schema-for value contains a single `schema: Schema` field.

`SchemaFor[T]` values are automatically derived for case classes using [Magnolia](https://propensive.com/opensource/magnolia/). 
It is possible to configure the automatic derivation to use snake-case, kebab-case or a custom field naming policy, 
by providing an implicit `tapir.generic.Configuration` value:

```scala
implicit val customConfiguration: Configuration =
  Configuration.default.withSnakeCaseMemberNames
```

## Media types

Codecs carry an additional type parameter, which specifies the media type. Some built-in media types include 
`text/plain`, `application/json` and `multipart/form-data`. Custom media types can be added by creating an 
implementation of the `tapir.MediaType` trait.

Thanks to codec being parametrised by media types, it is possible to have a `Codec[MyCaseClass, TextPlain, _]` which 
specifies how to serialize a case class to plain text, and a different `Codec[MyCaseClass, Json, _]`, which specifies 
how to serialize a case class to json. Both can be implicitly available without implicit resolution conflicts.

Different media types can be used in different contexts. When defining a path, query or header parameter, only a codec 
with the `TextPlain` media type can be used. However, for bodies, any media types is allowed. For example, the 
input/output described by `jsonBody[T]` requires a json codec.

## Custom types

Support for custom types can be added by writing a codec from scratch, or mapping over an existing codec. However,
custom types can also be supported by mapping over inputs/outputs, not codecs. When to use one and the other?

In general, codecs should be used when translating between raw values and "application-primitives". Codecs also
allow the decoding process to result in an error, or to complete successfully. For example, to support a custom id type:

```scala
def decode(s: String): DecodeResult[MyId] = MyId.parse(s) match {
  case Success(v) => DecodeResult.Value(v)
  case Failure(f) => DecodeResult.Error(s, f)
}
def encode(id: MyId): String = id.toString

implicit val myIdCodec: Codec[MyId] = Codec.stringPlainCodecUtf8.mapDecode(decode)(encode)
```

Additionally, if a type is supported by a codec, it can be used in multiple contexts, such as query parameters, headers,
bodies, etc. Mapped inputs by construction have a fixed context.

On the other hand, when building composite types out of many values, or when an isomorphic representation of a type
 is needed, but only for a single input/output/endpoint, mapping over an input/output is the simpler solution. Note that
 while codecs can report errors during decoding, mapping over inputs/outputs doesn't have this possibility.

## Validation

While codecs support reporting decoding failures, this is not meant as a validation solution, as it only works on single
values, while validation often involves multiple combined values.

Decoding failures should be reported when the input is in an incorrect low-level format, when parsing a "raw value"
fails. In other words, decoding failures should be reported for format failures, not business validation errors.

Any validation should be done as part of the "business logic" methods provided to the server interpreters. In case 
validation fails, the result can be an error, which is one of the mappings defined in an endpoint
(the `E` in `Endpoint[I, E, O, S]`).

## Next

Read on about [json support](json.html).
