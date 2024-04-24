package sttp.tapir.docs.openapi

import sttp.model.StatusCode
import sttp.apispec.{Schema => ASchema, SchemaType => ASchemaType}
import sttp.apispec.openapi._
import sttp.tapir._
import sttp.tapir.docs.apispec.DocsExtensionAttribute.RichEndpointIOInfo
import sttp.tapir.docs.apispec.{DocsExtensions, exampleValue}
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.internal._
import sttp.tapir.model.StatusCodeRange

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOperationResponse(
    tschemaToASchema: TSchemaToASchema,
    codecToMediaType: CodecToMediaType,
    options: OpenAPIDocsOptions
) {
  def apply(e: AnyEndpoint): ListMap[ResponsesKey, ReferenceOr[Response]] = {
    // There always needs to be at least a 200 empty response
    outputToResponses(e.output, ResponsesCodeKey(200), Some(Response.Empty)) ++
      inputToDefaultErrorResponses(e.securityInput.and(e.input)) ++
      outputToResponses(e.errorOutput, ResponsesDefaultKey, None)
  }

  private type StatusCodeKey = Option[Either[StatusCode, StatusCodeRange]]

  private def outputToResponses(
      output: EndpointOutput[_],
      defaultResponseKey: ResponsesKey,
      defaultResponse: Option[Response]
  ): ListMap[ResponsesKey, ReferenceOr[Response]] = {

    /** The list of status codes/status code ranges that are defined by the given outputs, or `List(None)` if there are none. */
    def statusCodeKeysInOutputs(os: Vector[EndpointOutput.Basic[_]]): List[StatusCodeKey] =
      os.collectFirst {
        case EndpointOutput.FixedStatusCode(statusCode, _, _)                             => List(Some(Left(statusCode)))
        case EndpointOutput.StatusCode(documentedCodes, _, _) if documentedCodes.nonEmpty => documentedCodes.keys.map(Some(_)).toList
      }.getOrElse(List(None))

    val outputs = output.asBasicOutputsList

    val statusCodeKeysAndOutputs: List[(StatusCodeKey, Vector[EndpointOutput.Basic[_]])] =
      outputs.flatMap(os => statusCodeKeysInOutputs(os).map(_ -> os))
    val outputsByStatusCodeKey: Map[StatusCodeKey, List[EndpointOutput.Basic[_]]] =
      statusCodeKeysAndOutputs.groupBy(_._1).mapValues(_.flatMap { case (_, output) => output }).toMap

    val statusCodeKeys: List[StatusCodeKey] = statusCodeKeysAndOutputs
      .map(_._1)
      .distinct
      .sortBy(_.map(_.fold(_.code, _.range * 100)).getOrElse(Integer.MAX_VALUE))

    val docsExtensions = outputs.flatMap(_.flatMap {
      case o: EndpointOutput.Atom[_]         => o.info.docsExtensions
      case EndpointIO.OneOfBody(variants, _) => variants.flatMap(_.info.docsExtensions)
    })
    statusCodeKeys.flatMap { sck =>
      val responseKey = sck
        .map {
          case Left(c)  => ResponsesCodeKey(c.code)
          case Right(r) => ResponsesRangeKey(r.range)
        }
        .getOrElse(defaultResponseKey)
      outputsToResponse(sck, outputsByStatusCodeKey.getOrElse(sck, List())).map(response =>
        (responseKey, Right(response.copy(extensions = DocsExtensions.fromIterable(docsExtensions))))
      )
    } match {
      case responses if responses.isEmpty => defaultResponse.map(defaultResponseKey -> Right(_)).toIterable.toListMap
      case responses                      => responses.toListMap
    }
  }

  private def outputsToResponse(sc: StatusCodeKey, outputs: List[EndpointOutput[_]]): Option[Response] = {
    val bodies = collectBodies(outputs)
    val headers = collectHeaders(outputs)

    val statusCodeDescriptions = outputs.flatMap {
      case EndpointOutput.StatusCode(documentedCodes, _, _) => documentedCodes.filter(c => sc.contains(c._1)).flatMap(_._2.description)
      case EndpointOutput.FixedStatusCode(_, _, EndpointIO.Info(Some(desc), _, _, _)) => Vector(desc)
      case EndpointIO.Empty(_, i) => if (i.description.nonEmpty) Vector(i.description.get) else Vector()
      case _                      => Vector()
    }

    val description = bodies.headOption.flatMap { case (desc, _) => desc }.getOrElse(statusCodeDescriptions.headOption.getOrElse(""))

    val content = bodies
      .flatMap { case (_, content) => content }
      .foldLeft(ListMap.empty[String, Vector[MediaType]]) { case (acc, (ct, mt)) =>
        acc.get(ct) match {
          case Some(mts) => acc.updated(ct, mts :+ mt)
          case None      => acc.updated(ct, Vector(mt))
        }
      }
      .mapValues(mergeMediaTypesToAnyOf)
      .toListMap

    if (bodies.nonEmpty || headers.nonEmpty) {
      Some(Response(description, headers.toListMap, content))
    } else if (outputs.nonEmpty) {
      Some(Response(description))
    } else if (sc.nonEmpty) {
      Some(Response.Empty)
    } else {
      None
    }
  }

  private def mergeMediaTypesToAnyOf(bodies: Vector[MediaType]): MediaType =
    bodies.toSet.toList match {
      case List(body) => body
      case bodies =>
        MediaType(
          schema = Some(ASchema(anyOf = bodies.flatMap(_.schema))),
          example = bodies.flatMap(_.example).headOption,
          examples = bodies.flatMap(_.examples).toListMap
        )
    }

  private def collectBodies(outputs: List[EndpointOutput[_]]): List[(Option[String], ListMap[String, MediaType])] = {
    val forcedContentType = extractFixedContentType(outputs)
    outputs.flatMap(_.traverseOutputs {
      case EndpointIO.Body(_, codec, info) => Vector((info.description, codecToMediaType(codec, info.examples, forcedContentType, Nil)))
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, info, _, _)) =>
        Vector((info.description, codecToMediaType(codec, info.examples, forcedContentType, Nil)))
    })
  }

  private def collectHeaders(outputs: List[EndpointOutput[_]]): List[(String, Right[Nothing, Header])] = {
    outputs.flatMap(_.traverseOutputs {
      case EndpointIO.Header(name, codec, info) =>
        Vector(
          name -> Right(
            Header(
              description = info.description,
              required = Some(!codec.schema.isOptional),
              schema = Some(tschemaToASchema(codec)),
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
              schema = Option(ASchema(ASchemaType.String))
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

  private def extractFixedContentType(outputs: List[EndpointOutput[_]]): Option[String] = {
    outputs
      .flatMap(_.traverseOutputs { case EndpointIO.FixedHeader(h, _, _) =>
        if (h.is("Content-Type")) Vector(h.value) else Vector.empty
      })
      .headOption
  }
}
