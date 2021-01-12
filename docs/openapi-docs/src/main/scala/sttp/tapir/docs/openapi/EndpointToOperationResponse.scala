package sttp.tapir.docs.openapi

import sttp.tapir._
import sttp.tapir.internal._
import sttp.tapir.apispec.ReferenceOr
import sttp.tapir.apispec.{Schema => ASchema, SchemaType => ASchemaType, _}
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.openapi.{Header, Response, ResponsesCodeKey, ResponsesDefaultKey, ResponsesKey}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOperationResponse(objectSchemas: Schemas, codecToMediaType: CodecToMediaType) {
  def apply(e: Endpoint[_, _, _, _]): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    // There always needs to be at least a 200 empty response
    outputToResponses(e.output, ResponsesCodeKey(200), Some(Response("", ListMap.empty, ListMap.empty))) ++
      outputToResponses(e.errorOutput, ResponsesDefaultKey, None)
  }

  private def outputToResponses(
      output: EndpointOutput[_],
      defaultResponseKey: ResponsesKey,
      defaultResponse: Option[Response]
  ): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    val responses: ListMap[ResponsesKey, ReferenceOr[Response]] = {
      val outputsList = output.asBasicOutputsList
      val byStatusCode = outputsList.groupBy(_._1)
      ListMap(
        outputsList
          .map(_._1)
          .distinct
          .flatMap { sc => byStatusCode.get(sc).map((sc, _)) }
          .flatMap { case (sc, responses) =>
            // using the "default" response key, if no status code is provided
            val responseKey = sc.map(c => ResponsesCodeKey(c.code)).getOrElse(defaultResponseKey)
            responses
              .flatMap { case (sc, outputs) => outputsToResponse(sc, outputs) }
              .reduceOption(_.merge(_))
              .map(response => (responseKey, Right(response)))
          }: _*
      )
    }

    if (responses.isEmpty) {
      // no output at all - using default if defined
      defaultResponse.map(defaultResponseKey -> Right(_)).toIterable.toListMap
    } else responses
  }

  private def outputsToResponse(sc: Option[sttp.model.StatusCode], outputs: Vector[EndpointOutput[_]]): Option[Response] = {
    val headers = outputs.collect {
      case EndpointIO.Header(name, codec, info) =>
        name -> Right(
          Header(
            info.description,
            Some(!codec.schema.isOptional),
            None,
            None,
            None,
            None,
            None,
            Some(objectSchemas(codec)),
            info.example.flatMap(exampleValue(codec, _)),
            ListMap.empty,
            ListMap.empty
          )
        )
      case EndpointIO.FixedHeader(h, _, info) =>
        h.name -> Right(
          Header(
            info.description,
            Some(true),
            None,
            None,
            None,
            None,
            None,
            Option(Right(ASchema(ASchemaType.String))),
            None,
            ListMap.empty,
            ListMap.empty
          )
        )
    }

    val bodies = outputs.collect {
      case EndpointIO.Body(_, codec, info) => (info.description, codecToMediaType(codec, info.examples))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, info, _)) =>
        (info.description, codecToMediaType(codec, info.examples))
    }
    val body = bodies.headOption

    val statusCodeDescriptions = outputs.flatMap {
      case EndpointOutput.StatusCode(possibleCodes, _, _)                          => possibleCodes.filter(c => sc.contains(c._1)).flatMap(_._2.description)
      case EndpointOutput.FixedStatusCode(_, _, EndpointIO.Info(Some(desc), _, _)) => Vector(desc)
      case _                                                                       => Vector()
    }

    val description = body.flatMap(_._1).getOrElse(statusCodeDescriptions.headOption.getOrElse(""))

    val content = body.map(_._2).getOrElse(ListMap.empty)

    if (body.isDefined || headers.nonEmpty) {
      Some(Response(description, headers.toListMap, content))
    } else if (outputs.nonEmpty) {
      Some(Response(description, ListMap.empty, ListMap.empty))
    } else {
      None
    }
  }
}
