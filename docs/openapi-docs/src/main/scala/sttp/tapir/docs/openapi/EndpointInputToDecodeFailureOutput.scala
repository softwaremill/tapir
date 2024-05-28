package sttp.tapir.docs.openapi

import sttp.model.MediaType
import sttp.tapir.internal._
import sttp.tapir.{Codec, CodecFormat, EndpointIO, EndpointInput, SchemaType}

import scala.annotation.tailrec

private[openapi] object EndpointInputToDecodeFailureOutput {
  def defaultBadRequestDescription(input: EndpointInput[_]): Option[String] = {
    val fallibleBasicInputs = input.asVectorOfBasicInputs(includeAuth = false).filter(inputMayFailWithBadRequest)

    if (fallibleBasicInputs.nonEmpty) Some(badRequestDescription(fallibleBasicInputs))
    else None
  }

  private def inputMayFailWithBadRequest(input: EndpointInput.Basic[_]) = input match {
    case EndpointInput.FixedMethod(_, _, _)     => false
    case EndpointInput.FixedPath(_, _, _)       => false
    case EndpointIO.Empty(_, _)                 => false
    case EndpointInput.PathCapture(_, codec, _) => decodingMayFail(codec)
    case EndpointIO.OneOfBody(variants, _)      => variants.exists(variant => decodingMayFail(variant.codec))
    case i: EndpointIO.Body[_, _]               => decodingMayFail(i.codec)
    case i: EndpointInput.Atom[_]               => decodingMayFail(i.codec) || !i.codec.schema.isOptional
  }

  private def decodingMayFail[CF <: CodecFormat](codec: Codec[_, _, CF]): Boolean =
    codec.format.mediaType != MediaType.TextPlain ||
      codec.schema.hasValidation ||
      codec.schema.format.nonEmpty ||
      codec.schema.schemaType != SchemaType.SString()

  private def badRequestDescription(fallibleBasicInputs: Vector[EndpointInput.Basic[_]]) =
    fallibleBasicInputs.map(failureSourceMessage).distinct.mkString(", ")

  /** Describes the source of the failure: in which part of the request did the failure occur. Currently the same as
    * `DefaultDecodeFailureHandler.FailureMessages.failureSourceMessage`
    */
  @tailrec
  def failureSourceMessage(input: EndpointInput[_]): String =
    input match {
      case EndpointInput.FixedMethod(_, _, _)      => s"Invalid value for: method"
      case EndpointInput.FixedPath(_, _, _)        => s"Invalid value for: path segment"
      case EndpointInput.PathCapture(name, _, _)   => s"Invalid value for: path parameter ${name.getOrElse("?")}"
      case EndpointInput.PathsCapture(_, _)        => s"Invalid value for: path"
      case EndpointInput.Query(name, _, _, _)      => s"Invalid value for: query parameter $name"
      case EndpointInput.QueryParams(_, _)         => "Invalid value for: query parameters"
      case EndpointInput.Cookie(name, _, _)        => s"Invalid value for: cookie $name"
      case _: EndpointInput.ExtractFromRequest[_]  => "Invalid value"
      case a: EndpointInput.Auth[_, _]             => failureSourceMessage(a.input)
      case _: EndpointInput.MappedPair[_, _, _, _] => "Invalid value"
      case _: EndpointIO.Body[_, _]                => s"Invalid value for: body"
      case _: EndpointIO.StreamBodyWrapper[_, _]   => s"Invalid value for: body"
      case EndpointIO.Header(name, _, _)           => s"Invalid value for: header $name"
      case EndpointIO.FixedHeader(name, _, _)      => s"Invalid value for: header $name"
      case EndpointIO.Headers(_, _)                => s"Invalid value for: headers"
      case _                                       => "Invalid value"
    }
}
