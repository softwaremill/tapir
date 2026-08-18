package sttp.tapir.docs.openapi

import sttp.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.apispec.openapi.Header
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.internal._
import sttp.tapir.{EndpointIO, EndpointOutput}

/** Converts an endpoint's outputs into OpenAPI response `Header`s, paired with their names.
  *
  * Shared by [[EndpointToOperationResponse]], which emits them into each response, and by [[ReusableComponentsForEndpoints]], which keys
  * its lookup by the generated `(name, Header)` value. As with [[EndpointToParameters]], two copies of this conversion could drift and
  * silently turn every `$ref` back into an inlined header, so there is one.
  */
private[openapi] class EndpointToHeaders(tschemaToASchema: TSchemaToASchema) {

  def apply(outputs: List[EndpointOutput[_]]): List[(String, Header)] = withSourceAtoms(outputs).map(_._2)

  /** The headers for the given outputs, each paired with the atom it was generated from, so that callers can read attributes (such as the
    * reusable-component marker) off the source atom.
    *
    * The cases are type tests delegating to generic helpers, rather than destructuring patterns: on Scala 2.12 a
    * `h @ EndpointIO.Header(...)` binding leaves the pattern's existential unsolvable, and severs the link between the codec's type and
    * `info.example`'s. This mirrors [[EndpointToParameters.withSourceAtoms]].
    */
  def withSourceAtoms(outputs: List[EndpointOutput[_]]): List[(EndpointIO.Atom[_], (String, Header))] =
    outputs.flatMap(_.traverseOutputs[(EndpointIO.Atom[_], (String, Header))] {
      case h: EndpointIO.Header[_]      => Vector(h -> (h.name -> headerToHeader(h)))
      case f: EndpointIO.FixedHeader[_] => Vector(f -> (f.h.name -> fixedHeaderToHeader(f)))
    })

  private def headerToHeader[T](header: EndpointIO.Header[T]): Header =
    Header(
      description = header.info.description,
      required = Some(!header.codec.schema.isOptional),
      schema = Some(tschemaToASchema(header.codec)),
      example = header.info.example.flatMap(exampleValue(header.codec, _))
    )

  private def fixedHeaderToHeader[T](header: EndpointIO.FixedHeader[T]): Header =
    Header(
      description = header.info.description,
      required = Some(true),
      schema = Option(ASchema(ASchemaType.String))
    )
}
