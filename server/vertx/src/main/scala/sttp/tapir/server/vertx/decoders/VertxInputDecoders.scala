package sttp.tapir.server.vertx.decoders

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.util.Date

import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.ext.web.RoutingContext
import sttp.model.Part
import sttp.tapir.internal.Params
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues, InputValuesResult}
import sttp.tapir.server.vertx.VertxEndpointOptions
import sttp.tapir.server.vertx.encoders.VertxOutputEncoders
import sttp.tapir.server.vertx.handlers.tryEncodeError
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling}
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, RawBodyType}

import scala.concurrent.{ExecutionContext, Future}
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
  private[vertx] def decodeBodyAndInputsThen[E](
      endpoint: Endpoint[_, E, _, _],
      rc: RoutingContext,
      logicHandler: Params => Unit
  )(implicit endpointOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Unit = {
    decodeBodyAndInputs(endpoint, rc).map {
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
    }(endpointOptions.executionContextOrCurrentCtx(rc)): Unit
  }

  private def decodeBodyAndInputs(e: Endpoint[_, _, _, _], rc: RoutingContext)(implicit
      serverOptions: VertxEndpointOptions
  ): Future[DecodeInputsResult] =
    decodeBody(DecodeInputs(e.input, new VertxDecodeInputsContext(rc)), rc)

  private def decodeBody(result: DecodeInputsResult, rc: RoutingContext)(implicit
      serverOptions: VertxEndpointOptions
  ): Future[DecodeInputsResult] = {
    implicit val ec: ExecutionContext = serverOptions.executionContextOrCurrentCtx(rc)
    result match {
      case values: DecodeInputsResult.Values =>
        values.bodyInput match {
          case None => Future.successful(values)
          case Some(bodyInput @ EndpointIO.Body(bodyType, codec, _)) =>
            extractRawBody(bodyType, rc).map(codec.decode).map {
              case DecodeResult.Value(body)      => values.setBodyInputValue(body)
              case failure: DecodeResult.Failure => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
            }
        }
      case failure: DecodeInputsResult.Failure => Future.successful(failure)
    }
  }

  private def extractRawBody[B](bodyType: RawBodyType[B], rc: RoutingContext)(implicit serverOptions: VertxEndpointOptions): Future[B] = {
    implicit val ec: ExecutionContext = serverOptions.executionContextOrCurrentCtx(rc)
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => Future.successful(rc.getBodyAsString(defaultCharset.toString).get)
      case RawBodyType.ByteArrayBody              => Future.successful(rc.getBody.get.getBytes)
      case RawBodyType.ByteBufferBody             => Future.successful(rc.getBody.get.getByteBuf.nioBuffer())
      case RawBodyType.InputStreamBody            => Future.successful(new ByteArrayInputStream(rc.getBody.get.getBytes))
      case RawBodyType.FileBody =>
        rc.fileUploads().toList match {
          case List(upload) =>
            Future.successful(new File(upload.uploadedFileName()))
          case List() if rc.getBody.isDefined =>
            val filePath = s"${serverOptions.uploadDirectory.getAbsolutePath}/tapir-${new Date().getTime}-${Random.nextLong()}"
            val fs = rc.vertx.fileSystem
            fs.createFileFuture(filePath).flatMap { _ =>
              fs.writeFileFuture(filePath, rc.getBody.get)
                .map(_ => new File(filePath))
            }
        }
      case RawBodyType.MultipartBody(partTypes, _) =>
        Future.successful(
          partTypes.map { case (partName, rawBodyType) =>
            Part(partName, extractPart(partName, rawBodyType, rc))
          }.toSeq
        )
    }
  }

  private def extractPart[B](name: String, bodyType: RawBodyType[B], rc: RoutingContext): B = {
    val formAttributes = rc.request.formAttributes
    val param = formAttributes.get(name)
    bodyType match {
      case RawBodyType.StringBody(charset) => new String(param.get.getBytes(charset))
      case RawBodyType.ByteArrayBody       => param.get.getBytes
      case RawBodyType.ByteBufferBody      => ByteBuffer.wrap(param.get.getBytes)
      case RawBodyType.InputStreamBody     => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody =>
        val f = rc.fileUploads.find(_.name == name).get
        new File(f.uploadedFileName())
      case RawBodyType.MultipartBody(partTypes, _) =>
        partTypes.map { case (partName, rawBodyType) =>
          Part(partName, extractPart(partName, rawBodyType, rc))
        }.toSeq
    }
  }

}
