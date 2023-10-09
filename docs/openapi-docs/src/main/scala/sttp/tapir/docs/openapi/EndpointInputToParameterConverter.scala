package sttp.tapir.docs.openapi

import sttp.apispec.Schema
import sttp.apispec.openapi.{MediaType, Parameter, ParameterIn}
import sttp.tapir.docs.apispec.DocsExtensionAttribute.RichEndpointIOInfo
import sttp.tapir.docs.apispec.DocsExtensions
import sttp.tapir.{Codec, EndpointIO, EndpointInput}

import scala.collection.immutable.ListMap

private[openapi] object EndpointInputToParameterConverter {
  def from[T](query: EndpointInput.Query[T], schema: Schema): Parameter = {
    val examples = ExampleConverter.convertExamples(query.codec, query.info.examples)

    Parameter(
      name = query.name,
      in = ParameterIn.Query,
      description = query.info.description,
      required = Some(!query.codec.schema.isOptional),
      deprecated = if (query.info.deprecated) Some(true) else None,
      schema = Some(schema),
      example = examples.singleExample,
      examples = examples.multipleExamples,
      extensions = DocsExtensions.fromIterable(query.info.docsExtensions),
      allowEmptyValue = query.flagValue.fold(None: Option[Boolean])(_ => Some(true))
    )
  }

  def from[T](query: EndpointInput.Query[T], content: ListMap[String, MediaType]): Parameter =
    Parameter(
      name = query.name,
      in = ParameterIn.Query,
      description = query.info.description,
      required = Some(!query.codec.schema.isOptional),
      deprecated = if (query.info.deprecated) Some(true) else None,
      schema = None,
      extensions = DocsExtensions.fromIterable(query.info.docsExtensions),
      content = content,
      allowEmptyValue = query.flagValue.fold(None: Option[Boolean])(_ => Some(true))
    )

  def from[T](pathCapture: EndpointInput.PathCapture[T], schema: Schema): Parameter = {
    val examples = ExampleConverter.convertExamples(pathCapture.codec, pathCapture.info.examples)
    Parameter(
      name = pathCapture.name.getOrElse("?"),
      in = ParameterIn.Path,
      description = pathCapture.info.description,
      required = Some(true),
      schema = Some(schema),
      example = examples.singleExample,
      examples = examples.multipleExamples,
      extensions = DocsExtensions.fromIterable(pathCapture.info.docsExtensions)
    )
  }

  def from[T](header: EndpointIO.Header[T], schema: Schema): Parameter = {
    val examples = ExampleConverter.convertExamples(header.codec, header.info.examples)
    Parameter(
      name = header.name,
      in = ParameterIn.Header,
      description = header.info.description,
      required = Some(!header.codec.schema.isOptional),
      deprecated = if (header.info.deprecated) Some(true) else None,
      schema = Some(schema),
      example = examples.singleExample,
      examples = examples.multipleExamples,
      extensions = DocsExtensions.fromIterable(header.info.docsExtensions)
    )
  }

  def from[T](header: EndpointIO.FixedHeader[T], schema: Schema): Parameter = {
    val baseExamples = ExampleConverter.convertExamples(header.codec, header.info.examples)
    val examples =
      if (baseExamples.multipleExamples.nonEmpty) baseExamples
      else
        ExampleConverter.convertExamples(Codec.string, List(EndpointIO.Example(header.h.value, None, None, None)))
    Parameter(
      name = header.h.name,
      in = ParameterIn.Header,
      description = header.info.description,
      required = Some(true),
      deprecated = if (header.info.deprecated) Some(true) else None,
      schema = Some(schema),
      example = examples.singleExample,
      examples = examples.multipleExamples,
      extensions = DocsExtensions.fromIterable(header.info.docsExtensions)
    )
  }

  def from[T](cookie: EndpointInput.Cookie[T], schema: Schema): Parameter = {
    val examples = ExampleConverter.convertExamples(cookie.codec, cookie.info.examples)
    Parameter(
      name = cookie.name,
      in = ParameterIn.Cookie,
      description = cookie.info.description,
      required = Some(!cookie.codec.schema.isOptional),
      deprecated = if (cookie.info.deprecated) Some(true) else None,
      schema = Some(schema),
      example = examples.singleExample,
      examples = examples.multipleExamples,
      extensions = DocsExtensions.fromIterable(cookie.info.docsExtensions)
    )
  }
}
