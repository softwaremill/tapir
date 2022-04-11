package sttp.tapir.server.stub

import sttp.client3.testing._
import sttp.client3.{Request, StreamBody}
import sttp.model._
import sttp.tapir.internal.RichOneOfBody
import sttp.tapir.server.interpreter.{DecodeBasicInputs, DecodeBasicInputsResult, DecodeInputsContext, RawValue}
import sttp.tapir.{Codec, DecodeResult, EndpointIO, EndpointInput, RawBodyType, StreamBodyIO}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

private[stub] object SttpRequestDecoder {
  def apply(request: Request[_, _], input: EndpointInput[_]): DecodeBasicInputsResult = {
    DecodeBasicInputs(input, DecodeInputsContext(new SttpRequest(request)))._1 match {
      case values: DecodeBasicInputsResult.Values =>
        values.bodyInputWithIndex match {
          case Some((Left(oneOfBodyInput), _)) =>
            def run[RAW, T](bodyInput: EndpointIO.Body[RAW, T]): DecodeBasicInputsResult = {
              bodyInput.codec.decode(rawBody(request, bodyInput)) match {
                case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
                case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
              }
            }

            val requestContentType: Option[String] = request.contentType
            oneOfBodyInput.chooseBodyToDecode(requestContentType.flatMap(MediaType.parse(_).toOption)) match {
              case Some(body) => run(body)
              case None =>
                DecodeBasicInputsResult.Failure(
                  oneOfBodyInput,
                  DecodeResult.Mismatch(oneOfBodyInput.show, requestContentType.getOrElse(""))
                ): DecodeBasicInputsResult
            }

          case Some((Right(bodyInput @ EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec: Codec[Any, Any, _], _, _, _))), _)) =>
            val value = request.body match {
              case StreamBody(s) => RawValue(s)
              case _             => throw new IllegalArgumentException("Raw body provided while endpoint accepts stream body")
            }
            codec.decode(value) match {
              case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
              case failure: DecodeResult.Failure => DecodeBasicInputsResult.Failure(bodyInput, failure): DecodeBasicInputsResult
            }
          case None => values
        }
      case failure: DecodeBasicInputsResult.Failure => failure
    }
  }

  private def rawBody[RAW](request: Request[_, _], body: EndpointIO.Body[RAW, _]): RAW = {
    val asByteArray = request.forceBodyAsByteArray
    body.bodyType match {
      case RawBodyType.StringBody(charset) => new String(asByteArray, charset)
      case RawBodyType.ByteArrayBody       => asByteArray
      case RawBodyType.ByteBufferBody      => ByteBuffer.wrap(asByteArray)
      case RawBodyType.InputStreamBody     => new ByteArrayInputStream(asByteArray)
      case RawBodyType.FileBody            => throw new UnsupportedOperationException
      case _: RawBodyType.MultipartBody    => throw new UnsupportedOperationException
    }
  }
}
