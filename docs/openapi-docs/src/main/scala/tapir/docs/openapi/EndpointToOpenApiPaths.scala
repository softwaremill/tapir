package tapir.docs.openapi

import tapir.docs.openapi.schema.ObjectSchemas
import tapir.model.Method
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi._
import tapir.internal._
import tapir._

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenApiPaths(objectSchemas: ObjectSchemas, securitySchemes: SecuritySchemes, options: OpenAPIDocsOptions) {

  private val codecToMediaType = new CodecToMediaType(objectSchemas)
  private val endpointToOperationResponse = new EndpointToOperationResponse(objectSchemas, codecToMediaType)

  def pathItem(e: Endpoint[_, _, _, _]): (String, PathItem) = {
    import model.Method._

    val inputs = e.input.asVectorOfBasicInputs(includeAuth = false)
    val pathComponents = namedPathComponents(inputs)
    val method = e.input.method.getOrElse(Method.GET)

    val pathComponentsForId = pathComponents.map(_.fold(identity, identity))
    val defaultId = options.operationIdGenerator(pathComponentsForId, method)

    val pathComponentForPath = pathComponents.map {
      case Left(p)  => s"{$p}"
      case Right(p) => p
    }

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

    ("/" + pathComponentForPath.mkString("/"), pathItem)
  }

  private def endpointToOperation(defaultId: String, e: Endpoint[_, _, _, _], inputs: Vector[EndpointInput.Basic[_]]): Operation = {
    val parameters = operationParameters(inputs)
    val body: Vector[ReferenceOr[RequestBody]] = operationInputBody(inputs)
    val responses: ListMap[ResponsesKey, ReferenceOr[Response]] = endpointToOperationResponse(e)

    val authNames = e.input.auths.flatMap(auth => securitySchemes.get(auth).map(_._1))
    // for now, all auths have empty scope
    val securityRequirement = authNames.map(_ -> Vector.empty).toListMap

    Operation(
      e.info.tags.toList,
      e.info.summary,
      e.info.description,
      defaultId,
      parameters.toList.map(Right(_)),
      body.headOption,
      responses,
      None,
      if (securityRequirement.isEmpty) List.empty else List(securityRequirement),
      List.empty
    )
  }

  private def operationInputBody(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case EndpointIO.Body(codec, info) =>
        Right(RequestBody(info.description, codecToMediaType(codec, info.example, None), Some(!codec.meta.isOptional)))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(s, mt, i)) =>
        Right(RequestBody(i.description, codecToMediaType(s, mt, i.example), Some(true)))
    }
  }

  private def operationParameters(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case q: EndpointInput.Query[_]       => queryToParameter(q)
      case p: EndpointInput.PathCapture[_] => pathCaptureToParameter(p)
      case h: EndpointIO.Header[_]         => headerToParameter(h)
      case c: EndpointInput.Cookie[_]      => cookieToParameter(c)
    }
  }

  private def headerToParameter[T](header: EndpointIO.Header[T]) = {
    EndpointInputToParameterConverter.from(header,
                                           objectSchemas(header.codec.meta.schema),
                                           header.info.example.flatMap(exampleValue(header.codec, _)))
  }
  private def cookieToParameter[T](cookie: EndpointInput.Cookie[T]) = {
    EndpointInputToParameterConverter.from(cookie,
                                           objectSchemas(cookie.codec.meta.schema),
                                           cookie.info.example.flatMap(exampleValue(cookie.codec, _)))
  }
  private def pathCaptureToParameter[T](p: EndpointInput.PathCapture[T]) = {
    EndpointInputToParameterConverter.from(p, objectSchemas(p.codec.meta.schema), p.info.example.flatMap(exampleValue(p.codec, _)))
  }

  private def queryToParameter[T](query: EndpointInput.Query[T]) = {
    EndpointInputToParameterConverter.from(query,
                                           objectSchemas(query.codec.meta.schema),
                                           query.info.example.flatMap(exampleValue(query.codec, _)))
  }

  /**
    * @return `Left` if the component is a capture, `Right` if it is a segment
    */
  private def namedPathComponents(inputs: Vector[EndpointInput.Basic[_]]): Vector[Either[String, String]] = {
    inputs
      .collect {
        case EndpointInput.PathCapture(_, name, _) => Left(name)
        case EndpointInput.PathSegment(s)          => Right(s)
      }
      .foldLeft((Vector.empty[Either[String, String]], 1)) {
        case ((acc, i), component) =>
          component match {
            case Left(None)    => (acc :+ Left(s"param$i"), i + 1)
            case Left(Some(p)) => (acc :+ Left(p), i)
            case Right(p)      => (acc :+ Right(p), i)
          }
      }
      ._1
  }
}
