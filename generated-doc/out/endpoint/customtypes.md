# Adding support for custom types

To support a custom type, you'll need to provide an implicit `Codec` for that type, or the components to create such
a codec. 

Most commonly, you'll be defining a custom codec so that a custom type can be used in inputs/outputs such as query 
parameters, path segments or headers. [Json](json.md) and [forms](forms.md) bodies have dedicated support for 
creating codecs, see the appropriate sections.

A custom codec can be created by writing one from scratch, mapping over an existing codec, or automatically deriving one.
Which of these approaches can be taken, depends on the context in which the codec will be used.

## Automatically deriving codecs

In some cases, codecs can be automatically derived:

* for supported [json](json.md) libraries
* for urlencoded and multipart [forms](forms.md)
* for value classes (extending `AnyVal`)

Automatic codec derivation usually requires other implicits, such as:

* json encoders/decoders from the json library
* codecs for individual form fields
* schema of the custom type, through the `Schema[T]` implicits (see the [next section on schemas](schemas.md))

Note that derivation of e.g. circe json encoders/decoders and tapir schemas are separate processes, and must be
configured separately.

## Creating an implicit codec by hand

To create a custom codec, you can either directly implement the `Codec` trait, which requires to provide the following
information:

* `encode` and `rawDecode` methods
* schema (for documentation and validation)
* codec format (`text/plain`, `application/json` etc.)

This might be quite a lot of work; that's why it's usually easier to map over an existing codec. To do that, you'll 
need to provide two mappings: 

* a `decode` method which decodes the lower-level type into the custom type, optionally reporting decode failures 
(the return type is a `DecodeResult`)
* an `encode` method which encodes the custom type into the lower-level type

For example, to support a custom id type:

```scala
import scala.util.*

class MyId private (id: String):
  override def toString(): String = id

object MyId:
  def parse(id: String): Try[MyId] = Success(new MyId(id))
```

```scala
import sttp.tapir.*
import sttp.tapir.CodecFormat.TextPlain

def decode(s: String): DecodeResult[MyId] = MyId.parse(s) match 
  case Success(v) => DecodeResult.Value(v)
  case Failure(f) => DecodeResult.Error(s, f)

def encode(id: MyId): String = id.toString

given Codec[String, MyId, TextPlain] = 
  Codec.string.mapDecode(decode)(encode)
```

Or, using the type alias for codecs in the `TextPlain` format and `String` as the raw value:

```scala
import sttp.tapir.Codec.PlainCodec

given PlainCodec[MyId] = Codec.string.mapDecode(decode)(encode)
```

```{note}
Note that inputs/outputs can also be mapped over. In some cases, it's enough to create an input/output corresponding 
to one of the existing types, and then map over them. However, if you have a type that's used multiple times, it's 
usually better to define a codec for that type. 
```

Then, you can use the new codec; e.g. to obtain an id from a query parameter, or a path segment:

```scala
endpoint.in(query[MyId]("myId"))
// or
endpoint.in(path[MyId])
```

## Next

Read on about [deriving schemas](schemas.md).
