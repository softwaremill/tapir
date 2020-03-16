package sttp.tapir.docs.openapi

import sttp.model.Method
import sttp.tapir._
import sttp.tapir.docs.openapi.schema.ObjectSchemas
import sttp.tapir.internal._
import sttp.tapir.openapi.OpenAPI.{ReferenceOr, SecurityRequirement}
import sttp.tapir.openapi.{Schema => OSchema, SchemaType => OSchemaType, _}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenApiPaths(objectSchemas: ObjectSchemas, securitySchemes: SecuritySchemes, options: OpenAPIDocsOptions) {
  private val codecToMediaType = new CodecToMediaType(objectSchemas)
  private val endpointToOperationResponse = new EndpointToOperationResponse(objectSchemas, codecToMediaType)

  def pathItem(e: Endpoint[_, _, _, _]): (String, PathItem) = {
    import Method._

    val inputs = e.input.asVectorOfBasicInputs(includeAuth = false)
    val pathComponents = namedPathComponents(inputs)
    val method = e.input.method.getOrElse(Method.GET)

    val defaultId = options.operationIdGenerator(pathComponents, method)

    val operation = Some(endpointToOperation(defaultId, e, inputs))
    val pathItem = PathItem(
      None,
      None,
      get = if (method == GET) operation else None,
      put = if (method == PUT) operation else None,
      post = if (method == POST) operation else None,
      delete = if (method == DELETE) operation else None,
      options = if (method == OPTIONS) operation else None,
      head = if (method == HEAD) operation else None,
      patch = if (method == PATCH) operation else None,
      trace = if (method == TRACE) operation else None,
      servers = List.empty,
      parameters = List.empty
    )

    (e.renderPathTemplate(renderQueryParam = None, includeAuth = false), pathItem)
  }

  private def endpointToOperation(defaultId: String, e: Endpoint[_, _, _, _], inputs: Vector[EndpointInput.Basic[_]]): Operation = {
    val parameters = operationParameters(inputs)
    val body: Vector[ReferenceOr[RequestBody]] = operationInputBody(inputs)
    val responses: ListMap[ResponsesKey, ReferenceOr[Response]] = endpointToOperationResponse(e)

    val securityRequirement: SecurityRequirement = e.input.auths.flatMap {
      case auth: EndpointInput.Auth.ScopedOauth2[_] => securitySchemes.get(auth).map(_._1).map((_, auth.requiredScopes.toVector))
      case auth                                     => securitySchemes.get(auth).map(_._1).map((_, Vector.empty))
    }.toListMap

    Operation(
      e.info.tags.toList,
      e.info.summary,
      e.info.description,
      e.info.name.getOrElse(defaultId),
      parameters.toList.map(Right(_)),
      body.headOption,
      responses,
      if (e.info.deprecated) Some(true) else None,
      if (securityRequirement.isEmpty) List.empty else List(securityRequirement),
      List.empty
    )
  }

  private def operationInputBody(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case EndpointIO.Body(codec, info) =>
        Right(RequestBody(info.description, codecToMediaType(codec, info.examples), Some(!codec.meta.schema.isOptional)))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(s, mt, i)) =>
        Right(RequestBody(i.description, codecToMediaType(s, mt, i.examples), Some(true)))
    }
  }

  private def operationParameters(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case q: EndpointInput.Query[_]       => queryToParameter(q)
      case p: EndpointInput.PathCapture[_] => pathCaptureToParameter(p)
      case h: EndpointIO.Header[_]         => headerToParameter(h)
      case c: EndpointInput.Cookie[_]      => cookieToParameter(c)
      case f: EndpointIO.FixedHeader       => fixedHeaderToParameter(f)
    }
  }

  private def headerToParameter[T](header: EndpointIO.Header[T]) = {
    EndpointInputToParameterConverter.from(
      header,
      objectSchemas(header.codec)
    )
  }

  private def fixedHeaderToParameter[T](header: EndpointIO.FixedHeader) = {
    EndpointInputToParameterConverter.from(
      header,
      Right(OSchema(OSchemaType.String))
    )
  }

  private def cookieToParameter[T](cookie: EndpointInput.Cookie[T]) = {
    EndpointInputToParameterConverter.from(
      cookie,
      objectSchemas(cookie.codec)
    )
  }
  private def pathCaptureToParameter[T](p: EndpointInput.PathCapture[T]) = {
    EndpointInputToParameterConverter.from(
      p,
      objectSchemas(p.codec)
    )
  }

  private def queryToParameter[T](query: EndpointInput.Query[T]) = {
    EndpointInputToParameterConverter.from(
      query,
      objectSchemas(query.codec)
    )
  }

  private def namedPathComponents(inputs: Vector[EndpointInput.Basic[_]]): Vector[String] = {
    inputs
      .collect {
        case EndpointInput.PathCapture(_, name, _) => Left(name)
        case EndpointInput.FixedPath(s)            => Right(s)
      }
      .foldLeft((Vector.empty[String], 1)) {
        case ((acc, i), component) =>
          component match {
            case Left(None)    => (acc :+ s"param$i", i + 1)
            case Left(Some(p)) => (acc :+ p, i)
            case Right(p)      => (acc :+ p, i)
          }
      }
      ._1
  }
}
