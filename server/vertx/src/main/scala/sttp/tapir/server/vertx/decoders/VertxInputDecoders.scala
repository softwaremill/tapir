package sttp.tapir.server.vertx.decoders

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.util.Date
import java.util.function.{Function => JFunction}
import io.vertx.ext.web.RoutingContext
import io.vertx.core.Future
import sttp.model.Part
import sttp.tapir.internal.Params
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues, InputValuesResult}
import sttp.tapir.server.vertx.VertxEndpointOptions
import sttp.tapir.server.vertx.encoders.VertxOutputEncoders
import sttp.tapir.server.vertx.handlers.tryEncodeError
import sttp.tapir.server.vertx.streams.ReadStreamCompatible
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling}
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, RawBodyType}

import scala.reflect.ClassTag
import scala.util.Random
import scala.collection.JavaConverters._

object VertxInputDecoders {

  /** Decodes the inputs, the body if needed, and if it succeeds invokes the logicHandler
    * @param endpoint the endpoint definition
    * @param rc the RoutingContext
    * @param logicHandler callback to execute if the inputs are decoded properly
    * @param endpointOptions endpoint options (execution context, etc.)
    * @param ect the error class to convert to, if the user recovers error
    * @tparam E Error parameter type, if it should be caught and handled by the user
    */
  private[vertx] def decodeBodyAndInputsThen[E, S: ReadStreamCompatible](
      endpoint: Endpoint[_, E, _, _],
      rc: RoutingContext,
      logicHandler: Params => Unit
  )(implicit
      endpointOptions: VertxEndpointOptions,
      ect: Option[ClassTag[E]]
  ): Unit = {
    decodeBodyAndInputs(endpoint, rc).map(scalaFuncToJava[DecodeInputsResult, Unit] {
      case values: DecodeInputsResult.Values =>
        InputValues(endpoint.input, values) match {
          case InputValuesResult.Value(params, _) =>
            logicHandler(params)
          case InputValuesResult.Failure(_, failure) =>
            tryEncodeError(endpoint, rc, failure)
        }
      case DecodeInputsResult.Failure(input, failure) =>
        val decodeFailureCtx = DecodeFailureContext(input, failure, endpoint)
        endpointOptions.decodeFailureHandler(decodeFailureCtx) match {
          case DecodeFailureHandling.NoMatch =>
            endpointOptions.logRequestHandling.decodeFailureNotHandled(endpoint, decodeFailureCtx)(endpointOptions.logger)
            rc.response.setStatusCode(404).end()
            ()
          case DecodeFailureHandling.RespondWithResponse(output, value) =>
            endpointOptions.logRequestHandling.decodeFailureHandled(endpoint, decodeFailureCtx, value)(endpointOptions.logger)
            VertxOutputEncoders.apply(output, value).apply(rc)
        }
    })

    ()
  }

  private def decodeBodyAndInputs[S: ReadStreamCompatible](e: Endpoint[_, _, _, _], rc: RoutingContext)(implicit
      serverOptions: VertxEndpointOptions
  ): Future[DecodeInputsResult] =
    decodeBody(DecodeInputs(e.input, new VertxDecodeInputsContext(rc)), rc)

  private def decodeBody(result: DecodeInputsResult, rc: RoutingContext)(implicit
      serverOptions: VertxEndpointOptions
  ): Future[DecodeInputsResult] = {
    result match {
      case values: DecodeInputsResult.Values =>
        values.bodyInput match {
          case None =>
            Future.succeededFuture(values)
          case Some(bodyInput @ EndpointIO.Body(bodyType, codec, _)) =>
            extractRawBody(bodyType, rc).flatMap(raw => Future.succeededFuture(codec.decode(raw))).flatMap {
              case DecodeResult.Value(body) =>
                Future.succeededFuture(values.setBodyInputValue(body))
              case failure: DecodeResult.Failure =>
                Future.succeededFuture(DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult)
            }
        }
      case failure: DecodeInputsResult.Failure => Future.succeededFuture(failure)
    }
  }

  private def extractRawBody[B](bodyType: RawBodyType[B], rc: RoutingContext)(implicit serverOptions: VertxEndpointOptions): Future[B] = {
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        Future.succeededFuture(Option(rc.getBodyAsString(defaultCharset.toString)).getOrElse(""))
      case RawBodyType.ByteArrayBody =>
        Future.succeededFuture(Option(rc.getBody).fold(Array.emptyByteArray)(_.getBytes))
      case RawBodyType.ByteBufferBody =>
        Future.succeededFuture(Option(rc.getBody).fold(ByteBuffer.allocate(0))(_.getByteBuf.nioBuffer()))
      case RawBodyType.InputStreamBody =>
        val bytes = Option(rc.getBody).fold(Array.emptyByteArray)(_.getBytes)
        Future.succeededFuture(new ByteArrayInputStream(bytes))
      case RawBodyType.FileBody =>
        rc.fileUploads().asScala.headOption match {
          case Some(upload) =>
            Future.succeededFuture(new File(upload.uploadedFileName()))
          case None if rc.getBody != null =>
            val filePath = s"${serverOptions.uploadDirectory.getAbsolutePath}/tapir-${new Date().getTime}-${Random.nextLong()}"
            val fs = rc.vertx.fileSystem
            val result = fs.createFile(filePath)
              .flatMap(_ => fs.writeFile(filePath, rc.getBody))
              .flatMap(_ => Future.succeededFuture(new File(filePath)))
            result
          case None =>
            Future.failedFuture[File]("No body")
        }
      case RawBodyType.MultipartBody(partTypes, defaultType) =>
        val defaultParts = defaultType
          .fold(Map.empty[String, RawBodyType[_]]) { bodyType =>
            val files = rc.fileUploads().asScala.map(_.name())
            val form = rc.request().formAttributes().names().asScala

            (files ++ form)
              .diff(partTypes.keySet)
              .map(_ -> bodyType)
              .toMap
          }
        val allParts = defaultParts ++ partTypes
        Future.succeededFuture(
          allParts.map { case (partName, rawBodyType) =>
            Part(partName, extractPart(partName, rawBodyType, rc))
          }.toSeq
        )
    }
  }

  private def extractPart[B](name: String, bodyType: RawBodyType[B], rc: RoutingContext): B = {
    bodyType match {
      case RawBodyType.StringBody(charset) => new String(readBytes(name, rc, charset))
      case RawBodyType.ByteArrayBody       => readBytes(name, rc, Charset.defaultCharset())
      case RawBodyType.ByteBufferBody      => ByteBuffer.wrap(readBytes(name, rc, Charset.defaultCharset()))
      case RawBodyType.InputStreamBody     => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody =>
        val f = rc.fileUploads.asScala.find(_.name == name).get
        new File(f.uploadedFileName())
      case RawBodyType.MultipartBody(partTypes, _) =>
        partTypes.map { case (partName, rawBodyType) =>
          Part(partName, extractPart(partName, rawBodyType, rc))
        }.toSeq
    }
  }

  private def readBytes(name: String, rc: RoutingContext, charset: Charset) = {
    val formAttributes = rc.request.formAttributes

    val formBytes = Option(formAttributes.get(name)).map(_.getBytes(charset))
    val fileBytes = rc.fileUploads().asScala.find(_.name() == name).map { upload =>
      Files.readAllBytes(Paths.get(upload.uploadedFileName()))
    }

    formBytes.orElse(fileBytes).get
  }

  implicit private def scalaFuncToJava[A, B](fn: A => B): JFunction[A, B] =
    new JFunction[A, B] {
      override def apply(a: A): B = fn(a)
    }
}
