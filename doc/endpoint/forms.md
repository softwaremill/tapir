# Forms

## URL-encoded forms

An URL-encoded form input/output can be specified in two ways. First, it is possible to map all form fields as a
`Seq[(String, String)]`, or `Map[String, String]` (which is more convenient if fields can't have multiple values):

```scala
formBody[Seq[(String, String)]]: EndpointIO[Seq[(String, String)], 
                                            MediaType.XWwwFormUrlencoded, _]
                                            
formBody[Map[String, String]]: EndpointIO[Map[String, String], 
                                          MediaType.XWwwFormUrlencoded, _]
```

Second, form data can be mapped to a case class. The codec for the case class is automatically derived using a macro at 
compile-time. The fields of the case class should have types, for which there is a plain text codec. For example:

```scala
case class RegistrationForm(name: String, age: Int, news: Boolean, city: Option[String])

formBody[RegistrationForm]
```

Each form-field is named the same as the case-class-field. The names can be transformed to snake or kebab case by 
providing an implicit `tapir.generic.Configuraton`. 

## Multipart forms

Similarly as above, multipart form input/outputs can be specified in two ways. To map to all parts of a multipart body,
use:

```scala
multipartBody[Seq[AnyPart]]: EndpointIO[Seq[AnyPart], MediaType.MultipartFormData, _]
```

where `type AnyPart = Part[_]`. `Part` is a case class containing the `name` of the part, disposition parameters,
headers, and the body. The bodies will be mappes as byte arrays (`Array[Byte]`), unless a custom multipart codec 
is defined using the `Codec.multipartCodec` method.

As with URL-encoded forms, multipart bodies can be mapped directly to case classes, however without the restriction
on codecs for individual fields. Given a field of type `T`, first a plain text codec is looked up, and if one isn't
found, any codec for any media type (e.g. JSON) is searched for.

Each part is named the same as the case-class-field. The names can be transformed to snake or kebab case by 
providing an implicit `tapir.generic.Configuraton`.
 
Additionally, the case class to which the multipart body is mapped can contain both normal fields, and fields of type 
`Part[T]`. This is useful, if part metadata (e.g. the filename) is relevant. 

For example:

```scala
case class RegistrationForm(userData: User, photo: Part[File], news: Boolean)

multipartBody[RegistrationForm]
```

## Next

Read on about [authentication](auth.html).
