package sttp.tapir.docs.openapi

import sttp.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.apispec.openapi.Parameter
import sttp.tapir._
import sttp.tapir.EndpointIO.OneOfBody
import sttp.tapir.docs.apispec.schema.TSchemaToASchema

/** Converts an endpoint's basic inputs into OpenAPI `Parameter`s.
  *
  * Shared, deliberately, by two callers:
  *   - [[EndpointToOpenAPIPaths]], which emits the parameters into each operation;
  *   - [[ReusableComponentsForEndpoints]], which needs the very same `Parameter` values, because the reusable-components lookup is keyed by
  *     the generated value rather than by input identity.
  *
  * If those two ever produced different values for the same input, no test would fail and no reference would be emitted — every `$ref`
  * would quietly become an inlined parameter again. Hence one implementation, not two.
  */
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
    case a: EndpointInput.Atom[_] if !a.codec.schema.hidden => a
  }

  /** The parameters for the given inputs, each paired with the atom it was generated from, so that callers can read attributes (such as the
    * reusable-component marker) off the source atom.
    */
  def withSourceAtoms(inputs: Vector[EndpointInput.Basic[_]]): Vector[(EndpointInput.Atom[_], Parameter)] = {
    inputs.collect {
      case q: EndpointInput.Query[_]       => (q, enrich(q, queryToParameter(q)))
      case p: EndpointInput.PathCapture[_] => (p, enrich(p, pathCaptureToParameter(p)))
      case h: EndpointIO.Header[_]         => (h, enrich(h, headerToParameter(h)))
      case c: EndpointInput.Cookie[_]      => (c, enrich(c, cookieToParameter(c)))
      case f: EndpointIO.FixedHeader[_]    => (f, enrich(f, fixedHeaderToParameter(f)))
    }
  }

  def apply(inputs: Vector[EndpointInput.Basic[_]]): Vector[Parameter] = withSourceAtoms(inputs).map(_._2)

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
