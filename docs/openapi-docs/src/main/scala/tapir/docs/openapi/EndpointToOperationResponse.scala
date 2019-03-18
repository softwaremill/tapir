package tapir.docs.openapi

import tapir.docs.openapi.schema.ObjectSchemas
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi._
import tapir.{Schema => SSchema, _}

private[openapi] class EndpointToOperationResponse(objectSchemas: ObjectSchemas, codecToMediaType: CodecToMediaType) {
  def apply(e: Endpoint[_, _, _, _]): Map[ResponsesKey, ReferenceOr[Response]] = {
    // There always needs to be at least a 200 empty response
    outputToResponses(e.output, ResponsesCodeKey(200), Some(Response("", Map.empty, Map.empty))) ++
      outputToResponses(e.errorOutput, ResponsesDefaultKey, None)
  }

  private def statusCodesToBodySchemas(io: EndpointIO[_]): Map[StatusCode, Option[SSchema]] = {
    io.traverse {
      case EndpointIO.StatusFrom(_: EndpointIO.Body[_, _, _], default, defaultSchema, whens) =>
        val fromWhens = whens.map {
          case (WhenClass(_, schema), statusCode) => statusCode -> Some(schema)
          case (_, statusCode)                    => statusCode -> None
        }
        (default -> defaultSchema) +: fromWhens
      case EndpointIO.StatusFrom(_, default, _, whens) =>
        val statusCodes = default +: whens.map(_._2)
        statusCodes.map(_ -> None)
    }.toMap
  }

  private def outputToResponses(io: EndpointIO[_],
                                defaultResponseKey: ResponsesKey,
                                defaultResponse: Option[Response]): Map[ResponsesKey, ReferenceOr[Response]] = {
    val statusCodes = statusCodesToBodySchemas(io)
    val responses = if (statusCodes.isEmpty) {
      // no status code mapping defined in the output - using the default response key, if there's any response defined at all
      outputToResponse(io, None).map(defaultResponseKey -> Right(_)).toMap
    } else {
      statusCodes.flatMap {
        case (statusCode, bodySchema) =>
          outputToResponse(io, bodySchema).map((ResponsesCodeKey(statusCode): ResponsesKey) -> Right(_))
      }
    }

    if (responses.isEmpty) {
      // no output at all - using default if defined
      defaultResponse.map(defaultResponseKey -> Right(_)).toMap
    } else responses
  }

  private def outputToResponse(io: EndpointIO[_], overrideBodySchema: Option[SSchema]): Option[Response] = {
    val ios = io.asVectorOfBasic()

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
      case EndpointIO.Body(m, i) => (i.description, codecToMediaType(m, i.example, overrideBodySchema))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(s, mt, i)) =>
        (i.description, codecToMediaType(overrideBodySchema.getOrElse(s), mt, i.example))
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
}
