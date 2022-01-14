package sttp.tapir.docs.openapi

import sttp.model.MediaType
import sttp.tapir.internal._
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.{Codec, CodecFormat, EndpointIO, EndpointInput, SchemaType}

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
    case EndpointIO.OneOfBody(variants, _)      => variants.exists(variant => decodingMayFail(variant.body.codec))
    case i: EndpointInput.Atom[_]               => decodingMayFail(i.codec) || !i.codec.schema.isOptional
  }

  private def decodingMayFail[CF <: CodecFormat](codec: Codec[_, _, CF]): Boolean =
    codec.format.mediaType != MediaType.TextPlain ||
      codec.schema.hasValidation ||
      codec.schema.format.nonEmpty ||
      codec.schema.schemaType != SchemaType.SString()

  private def badRequestDescription(fallibleBasicInputs: Vector[EndpointInput.Basic[_]]) =
    fallibleBasicInputs
      .map(input => DefaultDecodeFailureHandler.FailureMessages.failureSourceMessage(input))
      .distinct
      .mkString(", ")
}
