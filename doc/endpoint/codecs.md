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

Note that only textual bodies can be optional (optional binary/streaming bodies aren't supported). That's because a body
cannot be missing - there's always *some* body. This is unlike e.g. a query parameter: for which a value can be present,
a value can be empty (but defined!), or the parameter might be missing altogether - which corresponds to a `None`.
That's why only strict (non-streaming) textual bodies, which are empty (`""`), will be considered as an empty value 
(`None`), if the codec allows optional values. 

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

The schema is left unchanged when mapping over a codec, as the underlying representation of the value doesn't change.

When codecs are derived for complex types, e.g. for json mapping, schemas are looked up through implicit
`SchemaFor[T]` values. See [custom types](customtypes.html) for more details.

Tapir supports schema generation for coproduct types of the box. In order to extend openApi schema
representation a discriminator object can be specified. 

For example, given following coproduct:
```scala
sealed trait Entity{
  def kind: String
} 
case class Person(firstName:String, lastName:String) extends Entity {
  def kind: String = "person"
}
case class Organization(name: String) extends Entity {
  def kind: String = "org"  
}
```
The discriminator may look like:
```scala
val sPerson = implicitly[SchemaFor[Person]]
val sOrganization = implicitly[SchemaFor[Organization]]
implicit val sEntity: SchemaFor[Entity] = 
    SchemaFor.oneOf[Entity, String](_.kind, _.toString)("person" -> sPerson, "org" -> sOrganization)
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

## Next

Read on about [custom types](customtypes.html).
