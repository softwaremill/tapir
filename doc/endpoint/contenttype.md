# Content type

The endpoint's output content type is bound to the body outputs, that are specified for the endpoint (if any). The 
[codec](codecs.md) of a body output contains a `CodecFormat`, which in turns contains the `MediaType` instance.

## Codec formats and server interpreters

Codec formats define the *default* media type, which will be set as the `Content-Type` header. However, any 
user-provided value will override this default:

* dynamic content type, using `.out(header(HeaderNames.ContentType))`
* fixed content type, using e.g. `out(header(Header.contentType(MediaType.ApplicationJson)))`

## Multiple content types

Multiple, alternative content types can be specified using [`oneOf`](oneof.md). 

On the server side, the appropriate mapping will be chosen using content negotiation, via the `Accept` header, using
the [configurable](../server/options.md) `ContentTypeInterceptor`. Note that both the base media type, and the charset
for text types are taken into account.

On the client side, the appropriate mapping will be chosen basing on the `Content-Type` header value.

For example:

```scala
import sttp.tapir.*
import sttp.tapir.Codec.{JsonCodec, XmlCodec}
import sttp.model.StatusCode

case class Entity(name: String)
given JsonCodec[Entity] = ???
given XmlCodec[Entity] = ???

endpoint.out(
  oneOf(
    oneOfVariant(customCodecJsonBody[Entity]),
    oneOfVariant(xmlBody[Entity])
  )
)
```

For details on how to create codes manually or derive them automatically, see [custom types](customtypes.md) and the
subsequent section on json.

## Next

Read on about [json support](json.md).
