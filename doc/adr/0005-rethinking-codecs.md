# 5. Rethinking codecs

Date: 2020-05-01

## Context

Current codecs suffer from a couple of implementation issues: they are not composable; mapping an input/output
has less capabilities than mapping a codec; there are multiple types of codecs; encodings are specified in
two places, and it's not clear which one to pick (from the codec format or from the raw value type).

That's why the `Codec` class has been entirely redeisgned.

## Decision

A new base trait, `Mapping[L, H]`, is introduced, which establishes a correspondence between low-level values 
of type `L` and high-level values of type `H`. Decoding `L` to `H` might signal a decoding error.

This trait is extended by `Codec[L, H, CF]`. The third type parameter, codec format, differentiates between codecs
for same types, but different content types. It also provides a default value for the content type, if it's not 
specified explicitly.

An input requires a codec either from a single fixed type (such as a `String`), or from multiple fixed types 
(corresponding to one of `RawValueType`s - in case of bodies). However, codecs and mappings might also exist and
take part in composition between any other two types. The requirement is just on the final, composed form, which
is used when defining inputs/outputs.

String encodings are determined basing on context (for headers/query parameters/path segments), or basing on the
raw value type. The `RawValueType` is the only place, which can provide custom encoding to convert between a `String`
and bytes to be sent over the network.

Codecs also can contain schemas and validators. When extending a codec using `.map` with a subsequent codec or mapping,
the other schema/validator is discarded - the RHS is always treated as a plain `Mapping`. This might require overriding
schemas/validators by hand, but is less surprising as calling `.map` using a `Mapping` and using a `Codec` always does
the same thing.

Codecs for optional and multiple elements can be automatically derived from codecs for single elements, reporting
decoding errors if the arity of values doesn't match. Finally, codecs for inputs/outputs are still looked up implicitly,
with the user-provided codecs for simple types being automatically converted to required optional/list types. 