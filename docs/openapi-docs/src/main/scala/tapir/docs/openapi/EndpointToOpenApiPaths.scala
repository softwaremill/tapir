package tapir.docs.openapi

import tapir.docs.openapi.schema.ObjectSchemas
import tapir.model.Method
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{MediaType => OMediaType, _}
import tapir.{EndpointInput, MediaType => SMediaType, Schema => SSchema, _}

private[openapi] class EndpointToOpenApiPaths(objectSchemas: ObjectSchemas, options: OpenAPIDocsOptions) {

  def pathItem(e: Endpoint[_, _, _, _]): (String, PathItem) = {
    import model.Method._

    val inputs = e.input.asVectorOfBasic
    val pathComponents = namedPathComponents(inputs)
    val method = e.method.getOrElse(Method.GET)

    val pathComponentsForId = pathComponents.map(_.fold(identity, identity))
    val defaultId = options.operationIdGenerator(pathComponentsForId, method)

    val pathComponentForPath = pathComponents.map {
      case Left(p)  => s"{$p}"
      case Right(p) => p
    }

    val operation = Some(endpointToOperation(defaultId, e))
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

  private def endpointToOperation(defaultId: String, e: Endpoint[_, _, _, _]): Operation = {
    val inputs = e.input.asVectorOfBasic
    val parameters = operationParameters(inputs)
    val body: Vector[ReferenceOr[RequestBody]] = operationInputBody(inputs)
    val responses: Map[ResponsesKey, ReferenceOr[Response]] = operationResponse(e)
    Operation(
      e.info.tags.toList,
      e.info.summary,
      e.info.description,
      defaultId,
      parameters.toList.map(Right(_)),
      body.headOption,
      responses,
      None,
      List.empty
    )
  }

  private def operationInputBody(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case EndpointIO.Body(codec, info) =>
        Right(RequestBody(info.description, codecToMediaType(codec, info.example), Some(!codec.meta.isOptional)))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(s, mt, i)) =>
        Right(RequestBody(i.description, codecToMediaType(s, mt, i.example), Some(true)))
    }
  }

  private def operationParameters(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case q: EndpointInput.Query[_]       => queryToParameter(q)
      case p: EndpointInput.PathCapture[_] => pathCaptureToParameter(p)
      case h: EndpointIO.Header[_]         => headerToParameter(h)
    }
  }

  private def headerToParameter[T](header: EndpointIO.Header[T]) = {
    EndpointInputToParameterConverter.from(header,
                                           objectSchemas(header.codec.meta.schema),
                                           header.info.example.flatMap(exampleValue(header.codec, _)))
  }
  private def pathCaptureToParameter[T](p: EndpointInput.PathCapture[T]) = {
    EndpointInputToParameterConverter.from(p, objectSchemas(p.codec.meta.schema), p.info.example.flatMap(exampleValue(p.codec, _)))
  }

  private def queryToParameter[T](query: EndpointInput.Query[T]) = {
    EndpointInputToParameterConverter.from(query,
                                           objectSchemas(query.codec.meta.schema),
                                           query.info.example.flatMap(exampleValue(query.codec, _)))
  }

  private def operationResponse(e: Endpoint[_, _, _, _]): Map[ResponsesKey, Right[Nothing, Response]] = {
    List(
      outputToResponse(e.output).map { r =>
        ResponsesCodeKey(200) -> Right(r)
      },
      outputToResponse(e.errorOutput).map { r =>
        ResponsesDefaultKey -> Right(r)
      }
    ).flatten.toMap
  }

  private def outputToResponse(io: EndpointIO[_]): Option[Response] = {
    val ios = io.asVectorOfBasic

    val headers = ios.collect {
      case EndpointIO.Header(name, codec, info) =>
        name -> Right(
          Header(
            info.description,
            Some(!codec.meta.isOptional),
            None,
            None,
            None,
            None,
            None,
            Some(objectSchemas(codec.meta.schema)),
            info.example.flatMap(exampleValue(codec, _)),
            Map.empty,
            Map.empty
          ))
    }

    val bodies = ios.collect {
      case EndpointIO.Body(m, i)                                            => (i.description, codecToMediaType(m, i.example))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(s, mt, i)) => (i.description, codecToMediaType(s, mt, i.example))
    }
    val body = bodies.headOption

    val description = body.flatMap(_._1).getOrElse("")
    val content = body.map(_._2).getOrElse(Map.empty)

    if (body.isDefined || headers.nonEmpty) {
      Some(Response(description, headers.toMap, content))
    } else {
      None
    }
  }

  private def codecToMediaType[T, M <: SMediaType](o: CodecForOptional[T, M, _], example: Option[T]): Map[String, OMediaType] = {
    Map(
      o.meta.mediaType.mediaTypeNoParams -> OMediaType(Some(objectSchemas(o.meta.schema)),
                                                       example.flatMap(exampleValue(o, _)),
                                                       Map.empty,
                                                       Map.empty))
  }

  private def codecToMediaType[M <: SMediaType](schema: SSchema, mediaType: M, example: Option[String]): Map[String, OMediaType] = {
    Map(mediaType.mediaTypeNoParams -> OMediaType(Some(objectSchemas(schema)), example.map(ExampleValue), Map.empty, Map.empty))
  }

  private def exampleValue[T](v: Any): ExampleValue = ExampleValue(v.toString)
  private def exampleValue[T](codec: Codec[T, _, _], e: T): Option[ExampleValue] = Some(exampleValue(codec.encode(e)))
  private def exampleValue[T](codec: CodecForOptional[T, _, _], e: T): Option[ExampleValue] = codec.encode(e).map(exampleValue)
  private def exampleValue[T](codec: CodecForMany[T, _, _], e: T): Option[ExampleValue] = codec.encode(e).headOption.map(exampleValue)

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
