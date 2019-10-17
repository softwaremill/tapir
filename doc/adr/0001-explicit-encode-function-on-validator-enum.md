# 1. Explicit encode function on Validator.Enum

Date: 2019-10-03

## Context

To represent enum values in documentation, we need a way to encode them into appropriate raw values. However,
codecs and validators are created independently. The codec, to which a validator is added, can be a mapped codec,
a product, etc. 

As codecs are opaque (at least for now), given a codec we don't necessarily have a codec for the wrapped types or 
fields. Hence, even given a codec, we don't necessarily have an encode method for the value.

## Decision

As proposed in [PR240](https://github.com/softwaremill/tapir/pull/240), the `Enum` class will now contain an explicit 
encode function:

```scala
case class Enum[T](possibleValues: List[T], encode: Option[EncodeToAny[T]])
```

It might seem that another solution would be to require an implicit `Codec` instance when creating the enum validator,
however validators are often created as part of codec creation; this would create an infinite loop, e.g.:

```scala
implicit def plainCodecForColor: PlainCodec[Color] = {
  Codec.stringPlainCodecUtf8
    .map[Color]({
       case "red"  => Red
       case "blue" => Blue
    })(_.toString.toLowerCase)
    .validate(Validator.enum)
}
```

