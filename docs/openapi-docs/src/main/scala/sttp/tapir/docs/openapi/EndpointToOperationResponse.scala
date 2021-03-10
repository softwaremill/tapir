package sttp.tapir.docs.openapi

import sttp.tapir._
import sttp.tapir.apispec.{ReferenceOr, Schema => ASchema, SchemaType => ASchemaType, _}
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.internal._
import sttp.tapir.openapi._

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOperationResponse(objectSchemas: Schemas, codecToMediaType: CodecToMediaType) {
  def apply(e: Endpoint[_, _, _, _]): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    // There always needs to be at least a 200 empty response
    outputToResponses(e.output, ResponsesCodeKey(200), Some(Response.Empty)) ++
      outputToResponses(e.errorOutput, ResponsesDefaultKey, None)
  }

  private def outputToResponses(
      output: EndpointOutput[_],
      defaultResponseKey: ResponsesKey,
      defaultResponse: Option[Response]
  ): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    val outputs = output.asBasicOutputsList
    val byStatusCode = output.asBasicOutputsList.groupBy { case (sc, _) => sc }
    outputs.map { case (sc, _) => sc }.distinct.flatMap { sc =>
      val responseKey = sc.map(c => ResponsesCodeKey(c.code)).getOrElse(defaultResponseKey)
      val allOutputs = byStatusCode.get(sc).map(_.flatMap { case (_, output) => output }).getOrElse(List())
      outputsToResponse(sc, allOutputs).map(response => (responseKey, Right(response)))
    } match {
      case responses if responses.isEmpty => defaultResponse.map(defaultResponseKey -> Right(_)).toIterable.toListMap
      case responses => responses.toListMap
    }
  }

  private def outputsToResponse(sc: Option[sttp.model.StatusCode], outputs: List[EndpointOutput[_]]): Option[Response] = {
    val bodies = collectBodies(outputs)
    val headers = collectHeaders(outputs)

    val statusCodeDescriptions = outputs.flatMap {
      case EndpointOutput.StatusCode(possibleCodes, _, _)                          => possibleCodes.filter(c => sc.contains(c._1)).flatMap(_._2.description)
      case EndpointOutput.FixedStatusCode(_, _, EndpointIO.Info(Some(desc), _, _)) => Vector(desc)
      case _                                                                       => Vector()
    }

    val description = bodies.headOption.flatMap { case (desc, _) => desc }.getOrElse(statusCodeDescriptions.headOption.getOrElse(""))

    val content = bodies.flatMap { case (_, content) => content }.toListMap

    if (bodies.nonEmpty || headers.nonEmpty) {
      Some(Response(description, headers.toListMap, content))
    } else if (outputs.nonEmpty) {
      Some(Response(description, ListMap.empty, ListMap.empty))
    } else {
      None
    }
  }

  private def collectBodies(outputs: List[EndpointOutput[_]]): List[(Option[String], ListMap[String, MediaType])] = {
    outputs.collect {
      case EndpointIO.Body(_, codec, info) => (info.description, codecToMediaType(codec, info.examples))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, info, _)) =>
        (info.description, codecToMediaType(codec, info.examples))
    }
  }

  private def collectHeaders(outputs: List[EndpointOutput[_]]): List[(String, Right[Nothing, Header])] = {
    outputs.collect {
      case EndpointIO.Header(name, codec, info) =>
        name -> Right(
          Header(
            description = info.description,
            required = Some(!codec.schema.isOptional),
            schema = Some(objectSchemas(codec)),
            example = info.example.flatMap(exampleValue(codec, _))
          )
        )
      case EndpointIO.FixedHeader(h, _, info) =>
        h.name -> Right(
          Header(
            description = info.description,
            required = Some(true),
            schema = Option(Right(ASchema(ASchemaType.String)))
          )
        )
    }
  }
}
