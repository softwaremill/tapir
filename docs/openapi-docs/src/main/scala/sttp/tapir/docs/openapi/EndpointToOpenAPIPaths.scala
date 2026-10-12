package sttp.tapir.docs.openapi

import sttp.model.Method
import sttp.apispec.openapi._
import sttp.tapir._
import sttp.tapir.docs.apispec.DocsExtensionAttribute.{RichEndpointIOInfo, RichEndpointInfo}
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.docs.apispec.{DocsExtensions, SecurityRequirementsForEndpoints, SecuritySchemes, namedPathComponents}
import sttp.tapir.internal._

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenAPIPaths(
    tschemaToASchema: TSchemaToASchema,
    securitySchemes: SecuritySchemes,
    options: OpenAPIDocsOptions,
    reusableComponents: ReusableComponents
) {
  private val codecToMediaType = new CodecToMediaType(tschemaToASchema)
  private val endpointToParameters = new EndpointToParameters(tschemaToASchema)
  private val endpointToOperationResponse =
    new EndpointToOperationResponse(tschemaToASchema, codecToMediaType, options, reusableComponents)
  private val securityRequirementsForEndpoint = new SecurityRequirementsForEndpoints(securitySchemes)

  def pathItem(e: AnyEndpoint): (String, PathItem) = {
    import Method._

    val inputs = endpointToParameters.filterOutHiddenInputs(e.asVectorOfBasicInputs(includeAuth = false))
    val pathComponents = namedPathComponents(inputs)
    val method = e.method.getOrElse(Method.GET)

    val defaultId = options.operationIdGenerator(e, pathComponents, method)

    val operation = Some(endpointToOperation(defaultId, e, inputs))
    val pathItem = PathItem(
      get = if (method == GET) operation else None,
      put = if (method == PUT) operation else None,
      post = if (method == POST) operation else None,
      delete = if (method == DELETE) operation else None,
      options = if (method == OPTIONS) operation else None,
      head = if (method == HEAD) operation else None,
      patch = if (method == PATCH) operation else None,
      trace = if (method == TRACE) operation else None
    )

    (e.showPathTemplate(showQueryParam = None, includeAuth = false, showNoPathAs = "/", showPathsAs = None), pathItem)
  }

  private def endpointToOperation(defaultId: String, e: AnyEndpoint, inputs: Vector[EndpointInput.Basic[_]]): Operation = {
    val parameters: List[ReferenceOr[Parameter]] = endpointToParameters
      .withSourceAtoms(inputs)
      .map { case (atom, parameter) =>
        ReusableComponents
          .markerOf(atom)
          .flatMap(_ => reusableComponents.parameterToName.get(parameter))
          .map(name => Left(Reference.to("#/components/parameters/", name)): ReferenceOr[Parameter])
          .getOrElse(Right(parameter))
      }
      .distinct
      .toList
    val body: Vector[ReferenceOr[RequestBody]] = operationInputBody(inputs)
    val responses: ListMap[ResponsesKey, ReferenceOr[Response]] = endpointToOperationResponse(e)

    Operation(
      tags = e.info.tags.toList,
      summary = e.info.summary,
      description = e.info.description,
      operationId = e.info.name.orElse(Some(defaultId)),
      parameters = parameters,
      requestBody = body.headOption,
      responses = Responses(responses),
      deprecated = if (e.info.deprecated) Some(true) else None,
      security = securityRequirementsForEndpoint(e),
      extensions = DocsExtensions.fromIterable(e.info.docsExtensions)
    )
  }

  private def operationInputBody(inputs: Vector[EndpointInput.Basic[_]]) = {
    inputs.collect {
      case EndpointIO.Body(_, codec, info) =>
        Right(
          RequestBody(
            info.description,
            codecToMediaType(codec, info.examples, None, Nil),
            Some(!codec.schema.isOptional),
            DocsExtensions.fromIterable(info.docsExtensions)
          )
        )
      case EndpointIO.OneOfBody(variants, _) =>
        Right(
          RequestBody(
            variants.flatMap(_.info.description).headOption,
            variants
              .flatMap(variant => codecToMediaType(variant.codec, variant.info.examples, Some(variant.range.toString), Nil))
              .toListMap,
            Some(!variants.forall(_.codec.schema.isOptional)),
            DocsExtensions.fromIterable(variants.flatMap(_.info.docsExtensions))
          )
        )
      case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, info, _, encodedExamples)) =>
        Right(
          RequestBody(
            info.description,
            codecToMediaType(codec, info.examples, None, encodedExamples),
            Some(true),
            DocsExtensions.fromIterable(info.docsExtensions)
          )
        )
      case oneOf: OneOfInput[_, _] if inputContent(oneOf).nonEmpty =>
        val hasBodylessVariant = oneOf.variants.exists(v => !inputContainsBody(v.input))
        Right(
          RequestBody(
            inputDescription(oneOf),
            inputContent(oneOf),
            Some(!hasBodylessVariant),
            Nil
          )
        )
    }
  }

  private def inputContent(input: EndpointInput[_]): ListMap[String, sttp.apispec.openapi.MediaType] = input match {
    case EndpointIO.Body(_, codec, info) =>
      codecToMediaType(codec, info.examples, None, Nil)
    case EndpointIO.OneOfBody(variants, _) =>
      variants.flatMap(variant => codecToMediaType(variant.codec, variant.info.examples, Some(variant.range.toString), Nil)).toListMap
    case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, info, _, encodedExamples)) =>
      codecToMediaType(codec, info.examples, None, encodedExamples)
    case EndpointIO.Pair(left, right, _, _)     => inputContent(left) ++ inputContent(right)
    case EndpointInput.Pair(left, right, _, _)  => inputContent(left) ++ inputContent(right)
    case EndpointIO.MappedPair(wrapped, _)      => inputContent(wrapped)
    case EndpointInput.MappedPair(wrapped, _)   => inputContent(wrapped)
    case OneOfInput(variants, _)                => variants.flatMap(v => inputContent(v.input).toList).toListMap
    case _                                      => ListMap.empty
  }

  private def inputDescription(input: EndpointInput[_]): Option[String] = input match {
    case EndpointIO.Body(_, _, info)            => info.description
    case EndpointIO.OneOfBody(variants, _)      => variants.flatMap(_.info.description).headOption
    case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, _, info, _, _)) =>
      info.description
    case EndpointIO.Pair(left, right, _, _)     => inputDescription(left).orElse(inputDescription(right))
    case EndpointInput.Pair(left, right, _, _)  => inputDescription(left).orElse(inputDescription(right))
    case EndpointIO.MappedPair(wrapped, _)      => inputDescription(wrapped)
    case EndpointInput.MappedPair(wrapped, _)   => inputDescription(wrapped)
    case OneOfInput(variants, _)                => variants.view.map(v => inputDescription(v.input)).collectFirst { case Some(d) => d }
    case _                                      => None
  }
}
