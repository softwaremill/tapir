# Working with XML

Enabling support for XML is a matter of implementing proper [`XmlCodec[T]`](codecs.md) and providing it in scope.
This enables encoding objects to XML strings, and decoding XML strings to objects.
Implementation is fairly easy, and for now, one guide on how to integrate with scalaxb is provided.

```{note}
Note, that implementing `XmlCodec[T]` would require deriving not only XML library encoders/decoders, 
but also tapir related `Schema[T]`. These are completely separate - any customization e.g. for field
naming or inheritance strategies must be done separately for both derivations.
For more details see sections on [schema derivation](schemas.md) and on supporting [custom types](customtypes.md) in 
general.
```

## Scalaxb

If you possess the XML Schema definition file (`.xsd` file) consider using the scalaxb tool,
which generates needed models and serialization/deserialization logic.
To use the tool please follow the documentation on [setting up](https://scalaxb.org/setup) and
[running](https://scalaxb.org/running-scalaxb) scalaxb.

After code generation, create the `TapirXmlScalaxb` trait (or trait with another name of your choosing) and add the following code snippet:

```scala
import generated.`package`.defaultScope // import may differ depending on location of generated code
import scalaxb.XMLFormat // import may differ depending on location of generated code
import scalaxb.`package`.{fromXML, toXML} // import may differ depending on location of generated code
import sttp.tapir.Codec.XmlCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.{Codec, EndpointIO, Schema, stringBodyUtf8AnyFormat}

import scala.xml.{NodeSeq, XML}

trait TapirXmlScalaxb:
  case class XmlElementLabel(label: String)

  def xmlBody[T: XMLFormat: Schema](implicit l: XmlElementLabel): EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(scalaxbCodec[T])

  given (using XmlFormat[T], Schema[T], XmlElementLabel): XmlCodec[T] = 
    Codec.xml((s: String) =>
      try Value(fromXML[T](XML.loadString(s)))
      catch case e: Exception => Error(s, e)
    )((t: T) => {
      val nodeSeq: NodeSeq = toXML[T](obj = t, elementLabel = summon[XmlElementLabel].label, scope = defaultScope)
      nodeSeq.toString()
    })
```
This creates `XmlCodec[T]` that would encode / decode the types with `XMLFormat`, `Schema` and with `XmlElementLabel` provided in scope.
It also introduces `xmlBody` helper method, which allows you to easily express, that the declared endpoint consumes or returns XML.


Next to this trait, you might want to introduce `xml` package object to simplify imports.
```scala
package object xml extends TapirXmlScalaxb
```

From now on, XML serialization/deserialization would work for all classes generated from `.xsd` file as long as
`XMLFormat`, `Schema` and `XmlElementLabel` would be implicitly provided in the scope.
`XMLFormat` is scalaxb related, allowing for XML encoding / decoding.
[`Schema`](schemas.md) is tapir related, used primarily when generating documentation and validating incoming values.
And `XmlElementLabel` is required by scalaxb code when encoding to XML to give proper top node name.

Usage example:
```scala
import sttp.tapir.{PublicEndpoint, endpoint}
import cats.effect.IO
import generated.Outer // import may differ depending on location of generated code
import sttp.tapir.generic.auto.* // needed for Schema derivation
import sttp.tapir.server.ServerEndpoint

object Endpoints:
  import xml.* // imports tapir related serialization / deserialization logic

  given XmlElementLabel = XmlElementLabel("outer") // `label` is needed by scalaxb code to properly encode the top node of the xml

  val xmlEndpoint: PublicEndpoint[Outer, Unit, Outer, Any] = endpoint.post // `Outer` is a class generated by scalaxb based on .xsd file.
    .in("xml")
    .in(xmlBody[Outer])
    .out(xmlBody[Outer])
```

If the generation of OpenAPI documentation is required, consider adding OpenAPI doc extension on schema providing XML
namespace as described in the "Prefixes and Namespaces" section at [OpenAPI documentation regarding handling XML](https://swagger.io/docs/specification/data-models/representing-xml/).
This would add `xmlns` property to example request/responses at swagger, which is required by scalaxb to properly deserialize XML.
For more information on adding OpenAPI doc extension in tapir refer to [documentation](../docs/openapi.md#openapi-specification-extensions).

Adding xml namespace doc extension to `Outer`'s `Schema` example:
```scala
case class XmlNamespace(namespace: String)
given Schema[Outer] = summon[Derived[Schema[Outer]]].value
  .docsExtension("xml", XmlNamespace("http://www.example.com/innerouter"))
```

Also, you might want to check the repository with [example project](https://github.com/softwaremill/tapir-scalaxb-example) showcasing integration with tapir and scalaxb.
