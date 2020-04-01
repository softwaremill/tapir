# Codecs

## Mappings

The `Mapping[L, H]` trait defines a bi-directional mapping between values of type `L` and values of type `H`.

Low-level values of type `L` can be **decoded** to a higher-level value of type `H`. The decoding can fail; this is 
represented by a result of type `DecodeResult.Failure`. Failures might occur due to format errors, wrong arity, exceptions, 
or validation errors. Validators can be added through the `validate` method.

High-level values of type `H` can be **encoded** as a low-level value of type `L`.

Mappings can be chained using one of the `map` functions.

## Codecs

A `Codec[L, H, CF]` is a `Mapping[L, H]`, with additional meta-data: an optional schema and the format of the 
low-level value (more on that below). 

There are built-in codecs for most common types such as `String`, `Int` etc. Codecs are usually defined as implicit 
values and resolved implicitly when they are referenced. However, they can also be provided explicitly as needed.

For example, a `query[Int]("quantity")` specifies an input parameter which corresponds to the `quantity` query 
parameter and will be mapped as an `Int`. There's an implicit `Codec[List[String], Int, TextPlain]` value that is 
referenced by the `query` method (which is defined in the `sttp.tapir` package). 

In this example, the low-level value is a `List[String]`, as a given query parameter can be absent, have a single or 
many values. The high-level value is an `Int`. The codec will verify that there's a single query paramater with the 
given name, and parse it as an int. If any of this fails, a failure will be reported. 

In a server setting, if the value cannot be parsed as an int, a decoding failure is reported, and the endpoint 
won't match the request, or a `400 Bad Request` response is returned (depending on configuration).

As each codec is also a `Mapping`, codecs can be used to map endpoint inputs and outputs. In such cases, the
additional meta-data (schema & format) isn't taken into account.

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

A codec contains an optional schema, which describes how the high-level value is encoded when sent over the network. 
This schema information is used when generating documentation. 

Schemas consists of the schema type, which is one of the values defined in `SchemaType`, such as `SString`,
`SBinary`, `SArray` or `SProduct` (for objects). Moreover, a schema contains meta-data: value optionality,
description and low-level format.

For primitive types, the schema values are built-in, and defined in the `Schema` companion object.

The schema is left unchanged when mapping a codec or an input/output, as the underlying representation of the value 
doesn't change. However, schemas can be changed for individual inputs/outputs using the `.schema(Schema)` method.

When codecs are derived for complex types, e.g. for json mapping, schemas are looked up through implicit
`Schema[T]` values. See [custom types](customtypes.html) for more details.
 
## Codec format

Codecs contain an additional type parameter, which specifies the codec format. Each format corresponds to a media type,
which describes the low-level format of the raw value (to which the codec encodes). Some built-in formats include 
`text/plain`, `application/json` and `multipart/form-data`. Custom formats can be added by creating an 
implementation of the `sttp.tapir.CodecFormat` trait.

Thanks to codecs being parametrised by codec formats, it is possible to have a `Codec[String, MyCaseClass, TextPlain]` which 
specifies how to serialize a case class to plain text, and a different `Codec[String, MyCaseClass, Json]`, which specifies 
how to serialize a case class to json. Both can be implicitly available without implicit resolution conflicts.

Different codec formats can be used in different contexts. When defining a path, query or header parameter, only a codec 
with the `TextPlain` media type can be used. However, for bodies, any media types is allowed. For example, the 
input/output described by `jsonBody[T]` requires a json codec.

## Next

Read on about [custom types](customtypes.html).
