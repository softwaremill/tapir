package sttp.tapir.docs.openapi

import sttp.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.apispec.openapi.Parameter
import sttp.tapir._
import sttp.tapir.EndpointIO.OneOfBody
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.internal._

private[openapi] class EndpointToParameters(tschemaToASchema: TSchemaToASchema) {
  // stateless, and derived from the same TSchemaToASchema as every other caller's instance
  private val codecToMediaType = new CodecToMediaType(tschemaToASchema)

  def filterOutHiddenInputs(inputs: Vector[EndpointInput.Basic[_]]): Vector[EndpointInput.Basic[_]] = inputs.collect {
    // EndpointInput.Basic is either OneOfBody or Atom
    case OneOfBody(variants, mapping) =>
      OneOfBody(
        variants.filterNot(_.codec.schema.hidden),
        mapping
      )
    case a: EndpointInput.Atom[_] if !a.codec.schema.hidden && !isSecondaryBodyInput(a) => a
  }

  def withSourceAtoms(
      inputs: Vector[EndpointInput.Basic[_]],
      include: EndpointInput.Atom[_] => Boolean = _ => true
  ): Vector[(EndpointInput.Atom[_], Parameter)] = {
    inputs.collect {
      case q: EndpointInput.Query[_] if include(q)       => (q, enrich(q, queryToParameter(q)))
      case p: EndpointInput.PathCapture[_] if include(p) => (p, enrich(p, pathCaptureToParameter(p)))
      case h: EndpointIO.Header[_] if include(h)         => (h, enrich(h, headerToParameter(h)))
      case c: EndpointInput.Cookie[_] if include(c)      => (c, enrich(c, cookieToParameter(c)))
      case f: EndpointIO.FixedHeader[_] if include(f)    => (f, enrich(f, fixedHeaderToParameter(f)))
    }
  }

  private def headerToParameter[T](header: EndpointIO.Header[T]) =
    EndpointInputToParameterConverter.from(header, tschemaToASchema(header.codec))
  private def fixedHeaderToParameter(header: EndpointIO.FixedHeader[_]) =
    EndpointInputToParameterConverter.from(header, ASchema(ASchemaType.String))
  private def cookieToParameter[T](cookie: EndpointInput.Cookie[T]) =
    EndpointInputToParameterConverter.from(cookie, tschemaToASchema(cookie.codec))
  private def pathCaptureToParameter[T](p: EndpointInput.PathCapture[T]) =
    EndpointInputToParameterConverter.from(p, tschemaToASchema(p.codec))

  private def queryToParameter[T](query: EndpointInput.Query[T]) = query.codec.format match {
    // use `schema` for simple plain text scenarios and `content` for complex serializations, e.g. JSON
    // see https://swagger.io/docs/specification/describing-parameters/#schema-vs-content
    case CodecFormat.TextPlain() => EndpointInputToParameterConverter.from(query, tschemaToASchema(query.codec))
    case _ => EndpointInputToParameterConverter.from(query, codecToMediaType(query.codec, query.info.examples, None, Nil))
  }

  private def enrich(e: EndpointInput.Atom[_], p: Parameter): Parameter = addExplode(e, p)

  private def addExplode(e: EndpointInput.Atom[_], p: Parameter): Parameter =
    (e, e.codec.schema.attribute(Schema.Explode.Attribute)) match {
      // see https://swagger.io/specification/#parameter-object for defaults
      case ((_: EndpointInput.Query[_]), Some(Schema.Explode(false)))      => p.explode(false)
      case ((_: EndpointInput.Cookie[_]), Some(Schema.Explode(false)))     => p.explode(false)
      case ((_: EndpointIO.Header[_]), Some(Schema.Explode(true)))         => p.explode(true)
      case ((_: EndpointIO.FixedHeader[_]), Some(Schema.Explode(true)))    => p.explode(true)
      case ((_: EndpointInput.PathCapture[_]), Some(Schema.Explode(true))) => p.explode(true)
      case _                                                               => p
    }
}
