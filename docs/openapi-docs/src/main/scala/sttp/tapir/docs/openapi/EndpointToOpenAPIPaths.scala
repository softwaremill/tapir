package sttp.tapir.docs.openapi

import sttp.model.Method
import sttp.tapir._
import sttp.tapir.internal._
import sttp.tapir.apispec.{ReferenceOr, SecurityRequirement}
import sttp.tapir.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.tapir.docs.apispec.DocsExtensionAttribute.{RichEndpointIOInfo, RichEndpointInfo}
import sttp.tapir.docs.apispec.{SecuritySchemes, namedPathComponents}
import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.openapi.{Operation, PathItem, RequestBody, Response, Responses, ResponsesKey}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenAPIPaths(schemas: Schemas, securitySchemes: SecuritySchemes, options: OpenAPIDocsOptions) {
  private val codecToMediaType = new CodecToMediaType(schemas)
  private val endpointToOperationResponse = new EndpointToOperationResponse(schemas, codecToMediaType, options)

  def pathItem(e: AnyEndpoint): (String, PathItem) = {
    import Method._

    val inputs = e.asVectorOfBasicInputs(includeAuth = false)
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

    (e.renderPathTemplate(renderQueryParam = None, includeAuth = false), pathItem)
  }

  private def endpointToOperation(defaultId: String, e: AnyEndpoint, inputs: Vector[EndpointInput.Basic[_]]): Operation = {
    val parameters = operationParameters(inputs)
    val body: Vector[ReferenceOr[RequestBody]] = operationInputBody(inputs)
    val responses: ListMap[ResponsesKey, ReferenceOr[Response]] = endpointToOperationResponse(e)

    Operation(
      e.info.tags.toList,
      e.info.summary,
      e.info.description,
      e.info.name.orElse(Some(defaultId)),
      parameters.toList.map(Right(_)),
      body.headOption,
      Responses(responses),
      if (e.info.deprecated) Some(true) else None,
      operationSecurity(e),
      extensions = DocsExtensions.fromIterable(e.info.docsExtensions)
    )
  }

  private def operationSecurity(e: AnyEndpoint): List[SecurityRequirement] = {
    val auths = e.auths
    val securityRequirement: SecurityRequirement = auths.flatMap {
      case auth @ EndpointInput.Auth(_, _, _, info: EndpointInput.AuthInfo.ScopedOAuth2) =>
        securitySchemes.get(auth).map(_._1).map((_, info.requiredScopes.toVector))
      case auth => securitySchemes.get(auth).map(_._1).map((_, Vector.empty))
    }.toListMap

    val securityOptional = auths.flatMap(_.asVectorOfBasicInputs()).forall {
      case i: EndpointInput.Atom[_]          => i.codec.schema.isOptional
      case EndpointIO.OneOfBody(variants, _) => variants.forall(_.body.codec.schema.isOptional)
    }

    if (securityRequirement.isEmpty) List.empty
    else {
      if (securityOptional) {
        List(ListMap.empty: SecurityRequirement, securityRequirement)
      } else {
        List(securityRequirement)
      }
    }
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
            variants.collectFirst { case EndpointIO.OneOfBodyVariant(_, EndpointIO.Body(_, _, EndpointIO.Info(Some(d), _, _, _))) => d },
            variants
              .flatMap(variant => codecToMediaType(variant.body.codec, variant.body.info.examples, Some(variant.range.toString), Nil))
              .toListMap,
            Some(!variants.forall(_.body.codec.schema.isOptional)),
            DocsExtensions.fromIterable(variants.flatMap(_.body.info.docsExtensions))
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
      case q: EndpointInput.Query[_]       => queryToParameter(q)
      case p: EndpointInput.PathCapture[_] => pathCaptureToParameter(p)
      case h: EndpointIO.Header[_]         => headerToParameter(h)
      case c: EndpointInput.Cookie[_]      => cookieToParameter(c)
      case f: EndpointIO.FixedHeader[_]    => fixedHeaderToParameter(f)
    }
  }

  private def headerToParameter[T](header: EndpointIO.Header[T]) = {
    EndpointInputToParameterConverter.from(header, schemas(header.codec))
  }

  private def fixedHeaderToParameter[T](header: EndpointIO.FixedHeader[_]) = {
    EndpointInputToParameterConverter.from(header, Right(ASchema(ASchemaType.String)))
  }

  private def cookieToParameter[T](cookie: EndpointInput.Cookie[T]) = {
    EndpointInputToParameterConverter.from(cookie, schemas(cookie.codec))
  }

  private def pathCaptureToParameter[T](p: EndpointInput.PathCapture[T]) = {
    EndpointInputToParameterConverter.from(p, schemas(p.codec))
  }

  private def queryToParameter[T](query: EndpointInput.Query[T]) = {
    EndpointInputToParameterConverter.from(query, schemas(query.codec))
  }
}
