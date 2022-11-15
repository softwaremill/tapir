# Codecs

A `Codec[L, H, CF]` is a bi-directional mapping between low-level values of type `L` and high-level values of type `H`. 
Low level values are formatted as `CF`. A codec also contains the schema of the high-level value, which is used for
validation and documentation.

For example, a `Codec[String, User, CodecFormat.Json]` contains:
* a function to decode a `String` into `User`, which assumes that the string if formatted as JSON; this decoding might fail of course in case of malformed input
* a function to encode a `User` into a JSON `String`; this encoding step cannot fail

There are built-in implicit codecs for most common types such as `String`, `Int`, `Instant` etc., as well as some
types representing header values. Take a look at the `Codec` companion object for a full list. The companion object
also contains a number of helper methods to create custom codecs.

## Looking up codecs

For most inputs/outputs, the appropriate codec is required as an implicit parameter. Hence codec instances are usually 
defined as implicit values and resolved implicitly when they are referenced. However, they can also be provided 
explicitly as needed.

As an example, a `query[Int]("quantity")` specifies an input parameter which corresponds to the `quantity` query 
parameter and will be mapped as an `Int`. A query input requires a codec, where the low-level value is a `List[String]`
(representing potentially 0, one, or multiple parameters with the given name in the URL). Hence, an implicit 
`Codec[List[String], Int, TextPlain]` value will be looked up when using the `query` method (which is defined in the 
`sttp.tapir` package). 

In this example, the codec will verify that there's a single query parameter with the given name, and parse it as an 
integer. If any of this fails, a decode failure will be reported.

However, in some cases codecs aren't looked up as implicit values, instead being created from simpler components, which
themselves are looked up as implicits. This is the case e.g. for json bodies specified using `jsonBody`. The rationale 
behind such a design is that this provides better error reporting, in case the implicit components used to create the
codec are missing. Consult the signature of the specific input/output to learn what are its implicit requirements.

## Decode failures

In a server setting, if the value cannot be parsed as an int, a decoding failure is reported, and the endpoint 
won't match the request, or a `400 Bad Request` response is returned (depending on configuration). Take a look at
[server error handling](../server/errors.md) for more details.

## Optional and multiple parameters

Some inputs/outputs allow optional, or multiple parameters:

* path segments are always required
* query and header values can be optional or multiple (repeated query parameters/headers)
* bodies can be optional, but not multiple

In general, optional parameters are represented as `Option` values, and multiple parameters as `List` values.
For example, `header[Option[String]]("X-Auth-Token")` describes an optional header. An input described as 
`query[List[String]]("color")` allows multiple occurrences of the `color` query parameter, with all values gathered
into a list.

## Schemas

A codec contains a schema, which describes the high-level type. The schema is used when generating documentation
and enforcing validation rules.

Schema consists of:

* the schema type, which is one of the values defined in `SchemaType`, such as `SString`, `SBinary`, `SArray` 
  or `SProduct`/`SCoproduct` (for ADTs). This is the shape of the encoded value - as it is sent over the network
* meta-data: value optionality, description, example, default value and low-level format name
* validation rules

For primitive types, the schema values are built-in, and defined in the `Schema` companion object.

The schema is left unchanged when mapping a codec, or an input/output, as the underlying representation of the value
doesn't change. However, schemas can be changed for individual inputs/outputs using the `.schema(Schema)` method.

Schemas are typically referenced indirectly through codecs, and are specified when the codec is created. 

As part of deriving a codec, to support a custom or complex type (e.g. for json mapping), schemas can be looked up 
implicitly and derived as well. See [custom types](customtypes.md) for more details.
 
## Codec format

Codecs contain an additional type parameter, which specifies the codec format. Each format corresponds to a media type,
which describes the low-level format of the raw value (to which the codec encodes). Some built-in formats include 
`text/plain`, `application/json` and `multipart/form-data`. Custom formats can be added by creating an 
implementation of the `sttp.tapir.CodecFormat` trait.

Thanks to codecs being parametrised by codec formats, it is possible to have a `Codec[String, MyCaseClass, TextPlain]` which 
specifies how to serialize a case class to plain text, and a different `Codec[String, MyCaseClass, Json]`, which specifies 
how to serialize a case class to json. Both can be implicitly available without implicit resolution conflicts.

Different codec formats can be used in different contexts. When defining a path, query or header parameter, only a codec 
with the `TextPlain` media type can be used. However, for bodies, any media type is allowed. For example, the 
input/output described by `jsonBody[T]` requires a json codec.

## Next

Read on about [custom types](customtypes.md).
