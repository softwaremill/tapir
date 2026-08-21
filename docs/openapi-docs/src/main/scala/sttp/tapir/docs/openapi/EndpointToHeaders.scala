package sttp.tapir.docs.openapi

import sttp.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.apispec.openapi.Header
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.internal._
import sttp.tapir.{EndpointIO, EndpointOutput}

private[openapi] class EndpointToHeaders(tschemaToASchema: TSchemaToASchema) {

  def apply(outputs: List[EndpointOutput[_]]): List[(String, Header)] = withSourceAtoms(outputs).map(_._2)

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

  private def fixedHeaderToHeader(header: EndpointIO.FixedHeader[_]): Header =
    Header(
      description = header.info.description,
      required = Some(true),
      schema = Option(ASchema(ASchemaType.String))
    )
}
