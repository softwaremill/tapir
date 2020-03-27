# 4. Codecs parametrised by raw values

Date: 2019-11-22

## Context

Currently tapir's `Codec` is parametrised by: the high-level type, the codec format and the raw value (one of the 
supported ones, as defined by the `RawValueType` family). Can we drop the third parameter and just have
`Codec[T, CF]`?

## Decision

In short: yes, but at the expense of some type safety. In some contexts, we need to know that the raw value should
be a `String`: in headers, query parameters and the path. Hence, we need this restriction in place. 

In theory we could also accept other codecs and convert to/from strings as necessary. This could be done e.g. for byte 
arrays; however the question the is which charset to choose when converting a `String` to an `Array[Byte]` to be
consumed by the codec? Also, we would need to throw exceptions when the raw value would be a file or a multipart,
however cases where users would try to use e.g. a multipart codec for a header would be extremely rare, if ever.

Moreover, if somebody wanted to use the codecs directly (to encode/decode), the result would be an abstract `R` type,
instead of a specific one. This could make debugging, exploration or codec re-use harder.

User exposure to the `Codec` type should be limited anyway, as usually codecs for custom types are either derived
automatically, or customised basing on exisiting ones, for usage in headers/query parameters/path. In the latter
case, the raw type is usually `String` and the format `TextPlain`, in which case the single-paramtere type alias, 
`PlainCodec`, can be used.

That's why we keep the current design as-is.