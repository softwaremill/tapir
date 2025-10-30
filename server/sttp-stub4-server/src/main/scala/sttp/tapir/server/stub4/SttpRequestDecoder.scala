package sttp.tapir.server.stub4

import sttp.client4.testing._
import sttp.client4.{GenericRequest, StreamBody}
import sttp.model._
import sttp.tapir.internal.RichOneOfBody
import sttp.tapir.server.interpreter.{DecodeBasicInputs, DecodeBasicInputsResult, DecodeInputsContext, RawValue}
import sttp.tapir.{DecodeResult, EndpointIO, EndpointInput, InputStreamRange, RawBodyType}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

private[stub4] object SttpRequestDecoder {
  def apply(request: GenericRequest[_, _], input: EndpointInput[_]): DecodeBasicInputsResult = {
    DecodeBasicInputs(input, DecodeInputsContext(SttpRequest(request)))._1 match {
      case values: DecodeBasicInputsResult.Values =>
        def decodeBody[RAW, T](bodyInput: EndpointIO.Body[RAW, T]): DecodeBasicInputsResult = {
          bodyInput.codec.decode(rawBody(request, bodyInput)) match {
            case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
            case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
          }
        }

        def decodeStreamingBody(bodyInput: EndpointIO.StreamBodyWrapper[Any, Any]): DecodeBasicInputsResult = {
          val value = request.body match {
            case StreamBody(s) => RawValue(s)
            case _             => throw new IllegalArgumentException("Raw body provided while endpoint accepts stream body")
          }
          bodyInput.wrapped.codec.decode(value) match {
            case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
            case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
          }
        }

        values.bodyInputWithIndex match {
          case Some((Left(oneOfBodyInput), _)) =>
            val requestContentType: Option[String] = request.contentType
            oneOfBodyInput.chooseBodyToDecode(requestContentType.flatMap(MediaType.parse(_).toOption)) match {
              case Some(Left(body))                                          => decodeBody(body)
              case Some(Right(body: EndpointIO.StreamBodyWrapper[Any, Any])) => decodeStreamingBody(body)
              case None                                                      =>
                DecodeBasicInputsResult.Failure(
                  oneOfBodyInput,
                  DecodeResult.Mismatch(oneOfBodyInput.show, requestContentType.getOrElse(""))
                ): DecodeBasicInputsResult
            }

          case Some((Right(bodyInput: EndpointIO.StreamBodyWrapper[Any, Any]), _)) => decodeStreamingBody(bodyInput)
          case None                                                                => values
        }
      case failure: DecodeBasicInputsResult.Failure => failure
    }
  }

  private def rawBody[RAW](request: GenericRequest[_, _], body: EndpointIO.Body[RAW, _]): RAW = {
    val asByteArray = request.forceBodyAsByteArray
    body.bodyType match {
      case RawBodyType.StringBody(charset)  => new String(asByteArray, charset)
      case RawBodyType.ByteArrayBody        => asByteArray
      case RawBodyType.ByteBufferBody       => ByteBuffer.wrap(asByteArray)
      case RawBodyType.InputStreamBody      => new ByteArrayInputStream(asByteArray)
      case RawBodyType.FileBody             => throw new UnsupportedOperationException
      case RawBodyType.InputStreamRangeBody => new InputStreamRange(() => new ByteArrayInputStream(asByteArray))
      case _: RawBodyType.MultipartBody     => throw new UnsupportedOperationException
    }
  }
}
