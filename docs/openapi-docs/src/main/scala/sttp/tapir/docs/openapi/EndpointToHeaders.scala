package sttp.tapir.docs.openapi

import sttp.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.apispec.openapi.Header
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.internal._
import sttp.tapir.{EndpointIO, EndpointOutput}

private[openapi] class EndpointToHeaders(tschemaToASchema: TSchemaToASchema) {

  def withSourceAtoms(
      outputs: List[EndpointOutput[_]],
      include: EndpointIO.Atom[_] => Boolean = _ => true
  ): Vector[(EndpointIO.Atom[_], (String, Header))] =
    outputs.toVector.flatMap(_.traverseOutputs[(EndpointIO.Atom[_], (String, Header))] {
      case h: EndpointIO.Header[_] if include(h)      => Vector(h -> (h.name -> headerToHeader(h)))
      case f: EndpointIO.FixedHeader[_] if include(f) => Vector(f -> (f.h.name -> fixedHeaderToHeader(f)))
    })

  private def headerToHeader[T](header: EndpointIO.Header[T]): Header =
    Header(
      description = header.info.description,
      required = Some(!header.codec.schema.isOptional),
      schema = Some(tschemaToASchema(header.codec)),
      example = header.info.example.flatMap(exampleValue(header.codec, _))
    )

  private def fixedHeaderToHeader(header: EndpointIO.FixedHeader[_]): Header =
    Header(
      description = header.info.description,
      required = Some(true),
      schema = Option(ASchema(ASchemaType.String))
    )
}
