package sttp.tapir.docs.openapi

import sttp.model.Method
import sttp.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.apispec.openapi._
import sttp.tapir._
import sttp.tapir.EndpointIO.OneOfBody
import sttp.tapir.docs.apispec.DocsExtensionAttribute.{RichEndpointIOInfo, RichEndpointInfo}
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.docs.apispec.{DocsExtensions, SecurityRequirementsForEndpoints, SecuritySchemes, namedPathComponents}
import sttp.tapir.internal._

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenAPIPaths(
    tschemaToASchema: TSchemaToASchema,
    securitySchemes: SecuritySchemes,
    options: OpenAPIDocsOptions
) {
  private val codecToMediaType = new CodecToMediaType(tschemaToASchema)
  private val endpointToOperationResponse = new EndpointToOperationResponse(tschemaToASchema, codecToMediaType, options)
  private val securityRequirementsForEndpoint = new SecurityRequirementsForEndpoints(securitySchemes)

  def pathItem(e: AnyEndpoint): (String, PathItem) = {
    import Method._

    val inputs = filterOutHiddenInputs(e.asVectorOfBasicInputs(includeAuth = false))
    val pathComponents = namedPathComponents(inputs)
    val method = e.method.getOrElse(Method.GET)

    val defaultId = options.operationIdGenerator(e, pathComponents, method)

    val operation = Some(endpointToOperation(defaultId, e, inputs))
    val pathItem = PathItem(
      get = if (method == GET) operation else None,
      put = if (method == PUT) operation else None,
      post = if (method == POST) operation else None,
      delete = if (method == DELETE) operation else None,
      options = if (method == OPTIONS) operation else None,
      head = if (method == HEAD) operation else None,
      patch = if (method == PATCH) operation else None,
      trace = if (method == TRACE) operation else None
    )

    (e.showPathTemplate(showQueryParam = None, includeAuth = false, showNoPathAs = "/", showPathsAs = None), pathItem)
  }

  private def filterOutHiddenInputs(inputs: Vector[EndpointInput.Basic[_]]) = inputs.collect {
    // EndpointInput.Basic is either OneOfBody or Atom
    case OneOfBody(variants, mapping) =>
      OneOfBody(
        variants.filterNot(_.codec.schema.hidden),
        mapping
      )
    case a: EndpointInput.Atom[_] if !a.codec.schema.hidden => a
  }

  private def endpointToOperation(defaultId: String, e: AnyEndpoint, inputs: Vector[EndpointInput.Basic[_]]): Operation = {
    val parameters = operationParameters(inputs).distinct.toList
    val body: Vector[ReferenceOr[RequestBody]] = operationInputBody(inputs)
    val responses: ListMap[ResponsesKey, ReferenceOr[Response]] = endpointToOperationResponse(e)

    Operation(
      tags = e.info.tags.toList,
      summary = e.info.summary,
      description = e.info.description,
      operationId = e.info.name.orElse(Some(defaultId)),
      parameters = parameters.map(Right(_)),
      requestBody = body.headOption,
      responses = Responses(responses),
      deprecated = if (e.info.deprecated) Some(true) else None,
      security = securityRequirementsForEndpoint(e),
      extensions = DocsExtensions.fromIterable(e.info.docsExtensions)
    )
  }

  private def operationInputBody(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case EndpointIO.Body(_, codec, info) =>
        Right(
          RequestBody(
            info.description,
            codecToMediaType(codec, info.examples, None, Nil),
            Some(!codec.schema.isOptional),
            DocsExtensions.fromIterable(info.docsExtensions)
          )
        )
      case EndpointIO.OneOfBody(variants, _) =>
        Right(
          RequestBody(
            variants.flatMap(_.info.description).headOption,
            variants
              .flatMap(variant => codecToMediaType(variant.codec, variant.info.examples, Some(variant.range.toString), Nil))
              .toListMap,
            Some(!variants.forall(_.codec.schema.isOptional)),
            DocsExtensions.fromIterable(variants.flatMap(_.info.docsExtensions))
          )
        )
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, info, _, encodedExamples)) =>
        Right(
          RequestBody(
            info.description,
            codecToMediaType(codec, info.examples, None, encodedExamples),
            Some(true),
            DocsExtensions.fromIterable(info.docsExtensions)
          )
        )
    }
  }

  private def operationParameters(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case q: EndpointInput.Query[_]       => enrich(q, queryToParameter(q))
      case p: EndpointInput.PathCapture[_] => enrich(p, pathCaptureToParameter(p))
      case h: EndpointIO.Header[_]         => enrich(h, headerToParameter(h))
      case c: EndpointInput.Cookie[_]      => enrich(c, cookieToParameter(c))
      case f: EndpointIO.FixedHeader[_]    => enrich(f, fixedHeaderToParameter(f))
    }
  }

  private def headerToParameter[T](header: EndpointIO.Header[T]) =
    EndpointInputToParameterConverter.from(header, tschemaToASchema(header.codec))
  private def fixedHeaderToParameter[T](header: EndpointIO.FixedHeader[_]) =
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
