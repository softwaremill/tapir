package tapir.docs.openapi

import tapir.internal._
import tapir.docs.openapi.schema.ObjectSchemas
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi._
import tapir.{Schema => SSchema, _}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOperationResponse(objectSchemas: ObjectSchemas, codecToMediaType: CodecToMediaType) {
  def apply(e: Endpoint[_, _, _, _]): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    // There always needs to be at least a 200 empty response
    outputToResponses(e.output, ResponsesCodeKey(200), Some(Response("", ListMap.empty, ListMap.empty))) ++
      outputToResponses(e.errorOutput, ResponsesDefaultKey, None)
  }

  private def statusCodesToBodySchemas(output: EndpointOutput[_]): Map[StatusCode, Option[SSchema]] = {
    val r = output.traverseOutputs {
      case EndpointOutput.StatusFrom(_: EndpointIO.Body[_, _, _], default, defaultSchema, whens) =>
        val fromWhens = whens.map {
          case (WhenClass(_, schema), statusCode) => statusCode -> Some(schema)
          case (_, statusCode)                    => statusCode -> None
        }
        (default -> defaultSchema) +: fromWhens
      case EndpointOutput.StatusFrom(_, default, _, whens) =>
        val statusCodes = default +: whens.map(_._2)
        statusCodes.map(_ -> None)
    }

    r.toMap
  }

  private def outputToResponses(output: EndpointOutput[_],
                                defaultResponseKey: ResponsesKey,
                                defaultResponse: Option[Response]): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    val statusCodes = statusCodesToBodySchemas(output)
    val responses = if (statusCodes.isEmpty) {
      // no status code mapping defined in the output - using the default response key, if there's any response defined at all
      outputToResponse(output, None).map(defaultResponseKey -> Right(_)).toIterable.toListMap
    } else {
      statusCodes.toListMap.flatMap {
        case (statusCode, bodySchema) =>
          outputToResponse(output, bodySchema).map((ResponsesCodeKey(statusCode): ResponsesKey) -> Right(_))
      }
    }

    if (responses.isEmpty) {
      // no output at all - using default if defined
      defaultResponse.map(defaultResponseKey -> Right(_)).toIterable.toListMap
    } else responses
  }

  private def outputToResponse(output: EndpointOutput[_], overrideBodySchema: Option[SSchema]): Option[Response] = {
    val outputs = output.asVectorOfBasicOutputs

    val headers = outputs.collect {
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
            ListMap.empty,
            ListMap.empty
          ))
    }

    val bodies = outputs.collect {
      case EndpointIO.Body(m, i) => (i.description, codecToMediaType(m, i.example, overrideBodySchema))
      case EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(s, mt, i)) =>
        (i.description, codecToMediaType(overrideBodySchema.getOrElse(s), mt, i.example))
    }
    val body = bodies.headOption

    val description = body.flatMap(_._1).getOrElse("")
    val content = body.map(_._2).getOrElse(ListMap.empty)

    if (body.isDefined || headers.nonEmpty) {
      Some(Response(description, headers.toListMap, content))
    } else {
      None
    }
  }
}
