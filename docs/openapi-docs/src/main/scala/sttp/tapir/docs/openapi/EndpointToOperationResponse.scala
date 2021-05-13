package sttp.tapir.docs.openapi

import sttp.tapir._
import sttp.tapir.apispec.{ReferenceOr, Schema => ASchema, SchemaType => ASchemaType}
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.internal._
import sttp.tapir.openapi._

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOperationResponse(
    objectSchemas: Schemas,
    codecToMediaType: CodecToMediaType,
    options: OpenAPIDocsOptions
) {
  def apply(e: Endpoint[_, _, _, _]): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    // There always needs to be at least a 200 empty response
    outputToResponses(e.output, ResponsesCodeKey(200), Some(Response.Empty)) ++
      inputToDefaultErrorResponses(e.input) ++
      outputToResponses(e.errorOutput, ResponsesDefaultKey, None)
  }

  private def outputToResponses(
      output: EndpointOutput[_],
      defaultResponseKey: ResponsesKey,
      defaultResponse: Option[Response]
  ): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    val outputs = output.asBasicOutputsList
    val statusCodes = outputs.map { case (sc, _) => sc }.distinct
    val outputsByStatusCode = outputs.groupBy { case (sc, _) => sc }.mapValues(_.flatMap { case (_, output) => output })
    val docsExtensions = outputs.flatMap(_._2.flatMap(_.info.docsExtensions))
    statusCodes.flatMap { sc =>
      val responseKey = sc.map(c => ResponsesCodeKey(c.code)).getOrElse(defaultResponseKey)
      outputsToResponse(sc, outputsByStatusCode.getOrElse(sc, List())).map(response =>
        (responseKey, Right(response.copy(extensions = DocsExtensions.fromIterable(docsExtensions))))
      )
    } match {
      case responses if responses.isEmpty => defaultResponse.map(defaultResponseKey -> Right(_)).toIterable.toListMap
      case responses                      => responses.toListMap
    }
  }

  private def outputsToResponse(sc: Option[sttp.model.StatusCode], outputs: List[EndpointOutput[_]]): Option[Response] = {
    val bodies = collectBodies(outputs)
    val headers = collectHeaders(outputs)

    val statusCodeDescriptions = outputs.flatMap {
      case EndpointOutput.StatusCode(possibleCodes, _, _)                             => possibleCodes.filter(c => sc.contains(c._1)).flatMap(_._2.description)
      case EndpointOutput.FixedStatusCode(_, _, EndpointIO.Info(Some(desc), _, _, _)) => Vector(desc)
      case _                                                                          => Vector()
    }

    val description = bodies.headOption.flatMap { case (desc, _) => desc }.getOrElse(statusCodeDescriptions.headOption.getOrElse(""))

    val content = bodies.flatMap { case (_, content) => content }.toListMap

    if (bodies.nonEmpty || headers.nonEmpty) {
      Some(Response(Some(description), headers.toListMap, content))
    } else if (outputs.nonEmpty) {
      Some(Response(Some(description)))
    } else {
      None
    }
  }

  private def collectBodies(outputs: List[EndpointOutput[_]]): List[(Option[String], ListMap[String, MediaType])] =
    outputs.flatMap(_.traverseOutputs {
      case EndpointIO.Body(_, codec, info) => Vector((info.description, codecToMediaType(codec, info.examples)))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, info, _)) =>
        Vector((info.description, codecToMediaType(codec, info.examples)))
    })

  private def collectHeaders(outputs: List[EndpointOutput[_]]): List[(String, Right[Nothing, Header])] = {
    outputs.flatMap(_.traverseOutputs {
      case EndpointIO.Header(name, codec, info) =>
        Vector(
          name -> Right(
            Header(
              description = info.description,
              required = Some(!codec.schema.isOptional),
              schema = Some(objectSchemas(codec)),
              example = info.example.flatMap(exampleValue(codec, _))
            )
          )
        )
      case EndpointIO.FixedHeader(h, _, info) =>
        Vector(
          h.name -> Right(
            Header(
              description = info.description,
              required = Some(true),
              schema = Option(Right(ASchema(ASchemaType.String)))
            )
          )
        )
    })
  }

  private def inputToDefaultErrorResponses(input: EndpointInput[_]): ListMap[ResponsesKey, ReferenceOr[Response]] =
    options
      .defaultDecodeFailureOutput(input)
      .map(output => outputToResponses(output, ResponsesDefaultKey, None))
      .getOrElse(ListMap())
}
