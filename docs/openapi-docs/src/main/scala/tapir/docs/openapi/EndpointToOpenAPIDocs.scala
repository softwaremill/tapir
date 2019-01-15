package tapir.docs.openapi

import tapir.Schema.SObject
import tapir.docs.openapi.ObjectSchemasForEndpoints.SchemaKeys
import tapir.{MediaType => SMediaType, Schema => SSchema, _}
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{MediaType => OMediaType, Schema => OSchema, _}

object EndpointToOpenAPIDocs {
  def toOpenAPI(title: String, version: String, es: Iterable[Endpoint[_, _, _]]): OpenAPI = {
    val es2 = es.map(nameAllPathCapturesInEndpoint)
    val withSchemaKeys = new WithSchemaKeys(ObjectSchemasForEndpoints(es2))

    val base = OpenAPI(
      info = Info(title, None, None, version),
      servers = None,
      paths = Map.empty,
      components = withSchemaKeys.components
    )

    es2.map(withSchemaKeys.pathItem).foldLeft(base) {
      case (current, (path, pathItem)) =>
        current.addPathItem(path, pathItem)
    }
  }

  private class WithSchemaKeys(schemaKeys: SchemaKeys) {
    def pathItem(e: Endpoint[_, _, _]): (String, PathItem) = {
      import Method._

      val pathComponents = foldInputToVector(e.input, {
        case EndpointInput.PathCapture(_, name, _, _) => Vector(s"{${name.getOrElse("-")}}")
        case EndpointInput.PathSegment(s)             => Vector(s)
      })

      val defaultId = operationId(pathComponents, e.method)

      val pathItem = PathItem(
        None,
        None,
        get = if (e.method == GET) Some(operation(defaultId, e)) else None,
        put = if (e.method == PUT) Some(operation(defaultId, e)) else None,
        post = if (e.method == POST) Some(operation(defaultId, e)) else None,
        delete = if (e.method == DELETE) Some(operation(defaultId, e)) else None,
        options = if (e.method == OPTIONS) Some(operation(defaultId, e)) else None,
        head = if (e.method == HEAD) Some(operation(defaultId, e)) else None,
        patch = if (e.method == PATCH) Some(operation(defaultId, e)) else None,
        trace = if (e.method == TRACE) Some(operation(defaultId, e)) else None,
        servers = None,
        parameters = None
      )

      ("/" + pathComponents.mkString("/"), pathItem)
    }

    private def operationId(pathComponents: Vector[String], method: Method): String = {
      val pathComponentsOrRoot = if (pathComponents.isEmpty) {
        Vector("root")
      } else {
        pathComponents
      }

      // TODO parametrize the class with customizable id generation
      s"${pathComponentsOrRoot.mkString("-")}-${method.m.toLowerCase}"
    }

    private def operation(defaultId: String, e: Endpoint[_, _, _]): Operation = {

      val parameters = foldInputToVector(
        e.input, {
          case EndpointInput.Query(n, codec, d, ex) =>
            Vector(
              Parameter(
                n,
                ParameterIn.Query,
                d,
                Some(!codec.meta.isOptional),
                None,
                None,
                None,
                None,
                None,
                sschemaToReferenceOrOSchema(codec.meta.schema),
                ex.flatMap(exampleValue(codec, _)),
                None,
                None
              ))
          case EndpointInput.PathCapture(codec, n, d, ex) =>
            Vector(
              Parameter(
                n.getOrElse("?"),
                ParameterIn.Path,
                d,
                Some(true),
                None,
                None,
                None,
                None,
                None,
                sschemaToReferenceOrOSchema(codec.meta.schema),
                ex.flatMap(exampleValue(codec, _)),
                None,
                None
              ))
          case EndpointIO.Header(n, codec, d, ex) =>
            Vector(
              Parameter(
                n,
                ParameterIn.Header,
                d,
                Some(!codec.meta.isOptional),
                None,
                None,
                None,
                None,
                None,
                sschemaToReferenceOrOSchema(codec.meta.schema),
                ex.flatMap(exampleValue(codec, _)),
                None,
                None
              ))
        }
      )

      val body: Vector[ReferenceOr[RequestBody]] = foldInputToVector(
        e.input, {
          case EndpointIO.Body(codec, d, ex) =>
            Vector(Right(RequestBody(d, codecToMediaType(codec, ex), Some(!codec.meta.isOptional))))
        }
      )

      val responses: Map[ResponsesKey, ReferenceOr[Response]] =
        List(
          outputToResponse(e.output).map { r =>
            ResponsesCodeKey(200) -> Right(r)
          },
          outputToResponse(e.errorOutput).map { r =>
            ResponsesDefaultKey -> Right(r)
          }
        ).flatten.toMap

      Operation(
        noneIfEmpty(e.info.tags.toList),
        e.info.summary,
        e.info.description,
        defaultId,
        noneIfEmpty(parameters.toList.map(Right(_))),
        body.headOption,
        responses,
        None,
        None
      )
    }

    private def outputToResponse(io: EndpointIO[_]): Option[Response] = {
      val headers = foldIOToVector(
        io, {
          case EndpointIO.Header(name, codec, d, ex) =>
            Vector(
              name -> Right(Header(
                d,
                Some(!codec.meta.isOptional),
                None,
                None,
                None,
                None,
                None,
                Some(sschemaToReferenceOrOSchema(codec.meta.schema)),
                ex.flatMap(exampleValue(codec, _)),
                None,
                None
              )))
        }
      )

      val bodies = foldIOToVector(io, {
        case EndpointIO.Body(m, d, e) => Vector((d, codecToMediaType(m, e)))
      })
      val body = bodies.headOption

      val description = body.flatMap(_._1).getOrElse("")
      val content = body.map(_._2)

      if (body.isDefined || headers.nonEmpty) {
        Some(Response(description, Some(headers.toMap), content))
      } else {
        None
      }
    }

    private def codecToMediaType[T, M <: SMediaType](o: GeneralCodec[T, M, _], example: Option[T]): Map[String, OMediaType] = {
      Map(
        o.meta.mediaType.mediaTypeNoParams -> OMediaType(Some(sschemaToReferenceOrOSchema(o.meta.schema)),
                                                         example.flatMap(exampleValue(o, _)),
                                                         None,
                                                         None))
    }

    private def sschemaToReferenceOrOSchema(schema: SSchema): ReferenceOr[OSchema] = {
      schema match {
        case SObject(info, _, _) =>
          schemaKeys.get(info) match {
            case Some((key, _)) => Left(Reference("#/components/schemas/" + key))
            case None           => Right(SSchemaToOSchema(schema))
          }
        case _ => Right(SSchemaToOSchema(schema))
      }
    }

    def components: Option[Components] = {
      if (schemaKeys.nonEmpty) {
        Some(Components(Some(schemaKeys.map {
          case (_, (key, schema)) =>
            key -> Right(schema)
        })))
      } else None
    }

    private def exampleValue[T](codec: GeneralCodec[T, _, _], e: T): Option[ExampleValue] =
      codec.encodeOptional(e).map(v => ExampleValue(v.toString))
  }

  private def noneIfEmpty[T](l: List[T]): Option[List[T]] = if (l.isEmpty) None else Some(l)

  private def foldInputToVector[T](i: EndpointInput[_], f: PartialFunction[EndpointInput[_], Vector[T]]): Vector[T] = {
    i match {
      case _ if f.isDefinedAt(i)                  => f(i)
      case EndpointInput.Mapped(wrapped, _, _, _) => foldInputToVector(wrapped, f)
      case EndpointIO.Mapped(wrapped, _, _, _)    => foldInputToVector(wrapped, f)
      case EndpointInput.Multiple(inputs)         => inputs.flatMap(foldInputToVector(_, f))
      case EndpointIO.Multiple(inputs)            => inputs.flatMap(foldInputToVector(_, f))
      case _                                      => Vector.empty
    }
  }

  private def foldIOToVector[T](io: EndpointIO[_], f: PartialFunction[EndpointIO[_], Vector[T]]): Vector[T] = {
    io match {
      case _ if f.isDefinedAt(io)              => f(io)
      case EndpointIO.Mapped(wrapped, _, _, _) => foldIOToVector(wrapped, f)
      case EndpointIO.Multiple(inputs)         => inputs.flatMap(foldIOToVector(_, f))
      case _                                   => Vector.empty
    }
  }

  private def nameAllPathCapturesInEndpoint(e: Endpoint[_, _, _]): Endpoint[_, _, _] = {
    val (input2, _) = new EndpointInputMapper[Int](
      {
        case (EndpointInput.PathCapture(codec, None, description, example), i) =>
          (EndpointInput.PathCapture(codec, Some(s"p$i"), description, example), i + 1)
      },
      PartialFunction.empty
    ).mapInput(e.input, 1)

    e.copy(input = input2)
  }
}
