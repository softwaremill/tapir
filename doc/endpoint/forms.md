# Forms

## URL-encoded forms

An URL-encoded form input/output can be specified in two ways. First, it is possible to map all form fields as a
`Seq[(String, String)]`, or `Map[String, String]` (which is more convenient if fields can't have multiple values):

```scala mdoc:compile-only
import sttp.tapir._

formBody[Seq[(String, String)]]: EndpointIO.Body[String, Seq[(String, String)]]
formBody[Map[String, String]]: EndpointIO.Body[String, Map[String, String]]
```

Second, form data can be mapped to a case class. The codec for the case class is automatically derived using a macro at 
compile-time. The fields of the case class should have types, for which there is a plain text codec. For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.tapir.generic.auto._

case class RegistrationForm(name: String, age: Int, news: Boolean, city: Option[String])

formBody[RegistrationForm]: EndpointIO.Body[String, RegistrationForm]
```

Each form-field is named the same as the case-class-field. The names can be transformed to snake or kebab case by 
providing an implicit `tapir.generic.Configuraton`, or customised using the `@encodedName` annotation. 

## Multipart forms

Similarly as above, multipart form input/outputs can be specified in two ways. To map to all parts of a multipart body,
use:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.model.Part

multipartBody: EndpointIO.Body[Seq[RawPart], Seq[Part[Array[Byte]]]]
```

`Part` is a case class containing the `name` of the part, disposition parameters, headers, and the body. The bodies 
will be mapped as byte arrays (`Array[Byte]`). Custom multipart codecs can be defined with the `Codec.multipartCodec`
method, and then used with `multipartBody[T]`.

As with URL-encoded forms, multipart bodies can be mapped directly to case classes, however without the restriction
on codecs for individual fields. Given a field of type `T`, first a plain text codec is looked up, and if one isn't
found, any codec for any media type (e.g. JSON) is searched for.

Each part is named the same as the case-class-field. The names can be transformed to snake or kebab case by 
providing an implicit `sttp.tapir.generic.Configuraton`, or customised using the `@encodedName` annotation. 
 
Additionally, the case class to which the multipart body is mapped can contain both normal fields, and fields of type 
`Part[T]`. This is useful, if part metadata (e.g. the filename) is relevant.

For example:

```scala mdoc:compile-only
import sttp.tapir._
import sttp.model.Part
import java.io.File
import sttp.tapir.generic.auto._

case class RegistrationForm(userData: User, photo: Part[File], news: Boolean)
case class User(email: String)

multipartBody[RegistrationForm]: EndpointIO.Body[Seq[RawPart], RegistrationForm]
```

The fields can also be wrapped into `Option[T]` or `Part[Option[T]]` (`Option[Part[T]]` is unsupported) if the part is not required.

```scala mdoc:compile-only
import sttp.tapir._
import sttp.model.Part
import java.io.File
import sttp.tapir.generic.auto._

case class RegistrationForm(userData: Option[User], photo: Part[Option[File]], news: Option[Boolean])
case class User(email: String)

multipartBody[RegistrationForm]: EndpointIO.Body[Seq[RawPart], RegistrationForm]
```

### Custom codec

More complex multipart form data structures, like fields wrapping `Part` into containers, may require implementing custom codecs, for example:

```scala
case class Upload(files: List[Part[File]], title: Option[Part[String]])

def decode(parts: Seq[RawPart]): Upload = {
  val partsByName: Map[String, Seq[RawPart]] = parts.groupBy(_.name)
  val files: List[RawPart] = partsByName.get("files").map(_.toList).getOrElse(List.empty)
  val title: Option[RawPart] = partsByName.get("title").map(_.head)
  Upload(files.asInstanceOf[List[Part[File]]], title.asInstanceOf[Option[Part[String]]])
}
def encode(o: Upload): Seq[RawPart] = {
  o.files.map(e => e.copy(name = "files")) ++
    o.title.map(e => e.copy(name = "title")).toList
}

val filesCodec: Codec[FileRange, File, _ <: CodecFormat] = implicitly[Codec[FileRange, File, _ <: CodecFormat]]
val plainTexCodec: Codec[String, String, _ <: CodecFormat] = implicitly[Codec[String, String, CodecFormat.TextPlain]]
val schema: Schema[Upload] = implicitly[Schema[Upload]]

implicit val uploadCodec: MultipartCodec[Upload] = Codec
  .multipartWithoutGrouping(
    Map(
      "files" -> SinglePartCodec(RawBodyType.FileBody, filesCodec),
      "title" -> SinglePartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8), plainTexCodec)
    ),
    None
  )
  .map(decode _)(encode _)
  .schema(schema)
```
## Next

Read on about [security](security.md).
