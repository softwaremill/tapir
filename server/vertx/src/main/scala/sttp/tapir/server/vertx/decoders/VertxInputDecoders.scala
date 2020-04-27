package sttp.tapir.server.vertx.decoders

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.util.Date

import io.vertx.scala.ext.web.RoutingContext
import sttp.model.Part
import sttp.tapir.internal.Params
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues, InputValuesResult}
import sttp.tapir.server.vertx.VertxEndpointOptions
import sttp.tapir.server.vertx.encoders.VertxOutputEncoders
import sttp.tapir.server.vertx.handlers.tryEncodeError
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling}
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, RawBodyType}

import scala.reflect.ClassTag
import scala.util.Random

object VertxInputDecoders {

  /**
   * Decodes the inputs, the body if needed, and if it succeeds invokes the logicHandler
   * @param endpoint the endpoint definition
   * @param rc the RoutingContext
   * @param logicHandler callback to execute if the inputs are decoded properly
   * @param endpointOptions endpoint options (execution context, etc.)
   * @param ect the error class to convert to, if the user recovers error
   * @tparam E Error parameter type, if it should be caught and handled by the user
   */
  private [vertx] def decodeBodyAndInputsThen[E](
    endpoint: Endpoint[_, E, _, _],
    rc: RoutingContext,
    logicHandler: Params => Unit,
  )(implicit endpointOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Unit = {
    decodeBodyAndInputs(endpoint, rc) match {
      case values: DecodeInputsResult.Values =>
        InputValues(endpoint.input, values) match {
          case InputValuesResult.Value(params, _) =>
            logicHandler(params)
          case InputValuesResult.Failure(_, failure) =>
            tryEncodeError(endpoint, rc, failure)
        }
      case DecodeInputsResult.Failure(input, failure) =>
        val decodeFailureCtx = DecodeFailureContext(input, failure)
        endpointOptions.decodeFailureHandler(decodeFailureCtx) match {
          case DecodeFailureHandling.NoMatch =>
            endpointOptions.logRequestHandling.decodeFailureNotHandled(endpoint, decodeFailureCtx)(endpointOptions.logger)
            rc.response.setStatusCode(404).end()
          case DecodeFailureHandling.RespondWithResponse(output, value) =>
            endpointOptions.logRequestHandling.decodeFailureHandled(endpoint, decodeFailureCtx, value)(endpointOptions.logger)
            VertxOutputEncoders.apply(output, value)(endpointOptions)(rc)
        }

    }
  }

  private def decodeBodyAndInputs(e: Endpoint[_, _, _, _], rc: RoutingContext)
                                         (implicit serverOptions: VertxEndpointOptions): DecodeInputsResult =
    decodeBody(DecodeInputs(e.input, new VertxDecodeInputsContext(rc)), rc)

  private def decodeBody(result: DecodeInputsResult, rc: RoutingContext)
                        (implicit serverOptions: VertxEndpointOptions): DecodeInputsResult = {
    result match {
      case values: DecodeInputsResult.Values =>
        values.bodyInput match {
          case None => values
          case Some(bodyInput@EndpointIO.Body(bodyType, codec, _)) =>
            codec.decode(extractRawBody(bodyType, rc)) match {
              case DecodeResult.Value(body) => values.setBodyInputValue(body)
              case failure: DecodeResult.Failure => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
            }
        }
      case failure: DecodeInputsResult.Failure => failure
    }
  }

  private def extractRawBody[B](bodyType: RawBodyType[B], rc: RoutingContext)
                       (implicit serverOptions: VertxEndpointOptions): Any =
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => rc.getBodyAsString(defaultCharset.toString).get
      case RawBodyType.ByteArrayBody => rc.getBodyAsString.get.getBytes()
      case RawBodyType.ByteBufferBody => rc.getBody.get.getByteBuf.nioBuffer()
      case RawBodyType.InputStreamBody =>
        new ByteArrayInputStream(rc.getBody.get.getBytes) // README: be really careful with that
      case RawBodyType.FileBody =>
        rc.fileUploads().toList match {
          case List(upload) =>
            new File(upload.uploadedFileName())
          case List() if rc.getBody.isDefined => // README: really weird, but there's a test that sends the body as String, and expects a File
            val filePath = s"${serverOptions.uploadDirectory.getAbsolutePath}/tapir-${new Date().getTime}-${Random.nextLong()}"
            try {
              rc.vertx().fileSystem().createFileBlocking(filePath)
              rc.vertx().fileSystem().writeFileBlocking(filePath, rc.getBody.get)
              new File(filePath)
            } catch {
              case e: Throwable => new File("")
            }
          case _ => throw new IllegalArgumentException("Cannot expect a file to be returned without sending body or file upload")
        }
      case RawBodyType.MultipartBody(partTypes, _) =>
        partTypes.map { case (partName, rawBodyType) =>
          Part(partName, extractPart(partName, rawBodyType, rc))
        }
    }

  private def extractPart(name: String, bodyType: RawBodyType[_], rc: RoutingContext): Any = {
    val formAttributes = rc.request.formAttributes
    val param = formAttributes.get(name)
    bodyType match {
      case RawBodyType.StringBody(charset) => new String(param.get.getBytes(charset))
      case RawBodyType.ByteArrayBody => param.get.getBytes
      case RawBodyType.ByteBufferBody => ByteBuffer.wrap(param.get.getBytes)
      case RawBodyType.InputStreamBody => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody =>
        val f = rc.fileUploads.find(_.name == name).get
        new File(f.uploadedFileName())
      case RawBodyType.MultipartBody(partTypes, _) =>
        partTypes.map { case (partName, rawBodyType) =>
          Part(partName, extractPart(partName, rawBodyType, rc))
        }
    }
  }


}
