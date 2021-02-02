package sttp.tapir.docs.openapi

import sttp.tapir.apispec.{ReferenceOr, Schema}
import sttp.tapir.openapi.{Parameter, ParameterIn}
import sttp.tapir.{Codec, EndpointIO, EndpointInput}

import scala.collection.immutable.ListMap

private[openapi] object EndpointInputToParameterConverter {
  def from[T](query: EndpointInput.Query[T], schema: ReferenceOr[Schema]): Parameter = {
    val examples = ExampleConverter.convertExamples(query.codec, query.info.examples)

    Parameter(
      query.name,
      ParameterIn.Query,
      query.info.description,
      Some(!query.codec.schema.isOptional),
      if (query.info.deprecated) Some(true) else None,
      None,
      None,
      None,
      None,
      schema,
      examples.singleExample,
      examples.multipleExamples,
      ListMap.empty
    )
  }

  def from[T](pathCapture: EndpointInput.PathCapture[T], schema: ReferenceOr[Schema]): Parameter = {
    val examples = ExampleConverter.convertExamples(pathCapture.codec, pathCapture.info.examples)
    Parameter(
      pathCapture.name.getOrElse("?"),
      ParameterIn.Path,
      pathCapture.info.description,
      Some(true),
      None,
      None,
      None,
      None,
      None,
      schema,
      examples.singleExample,
      examples.multipleExamples,
      ListMap.empty
    )
  }

  def from[T](header: EndpointIO.Header[T], schema: ReferenceOr[Schema]): Parameter = {
    val examples = ExampleConverter.convertExamples(header.codec, header.info.examples)
    Parameter(
      header.name,
      ParameterIn.Header,
      header.info.description,
      Some(!header.codec.schema.isOptional),
      if (header.info.deprecated) Some(true) else None,
      None,
      None,
      None,
      None,
      schema,
      examples.singleExample,
      examples.multipleExamples,
      ListMap.empty
    )
  }

  def from[T](header: EndpointIO.FixedHeader[T], schema: ReferenceOr[Schema]): Parameter = {
    val baseExamples = ExampleConverter.convertExamples(header.codec, header.info.examples)
    val examples =
      if (baseExamples.multipleExamples.nonEmpty) baseExamples
      else
        ExampleConverter.convertExamples(Codec.string, List(EndpointIO.Example(header.h.value, None, None)))
    Parameter(
      header.h.name,
      ParameterIn.Header,
      header.info.description,
      Some(true),
      if (header.info.deprecated) Some(true) else None,
      None,
      None,
      None,
      None,
      schema,
      examples.singleExample,
      examples.multipleExamples,
      ListMap.empty
    )
  }

  def from[T](cookie: EndpointInput.Cookie[T], schema: ReferenceOr[Schema]): Parameter = {
    val examples = ExampleConverter.convertExamples(cookie.codec, cookie.info.examples)
    Parameter(
      cookie.name,
      ParameterIn.Cookie,
      cookie.info.description,
      Some(!cookie.codec.schema.isOptional),
      if (cookie.info.deprecated) Some(true) else None,
      None,
      None,
      None,
      None,
      schema,
      examples.singleExample,
      examples.multipleExamples,
      ListMap.empty
    )
  }
}
