package sttp.tapir.server.akkahttp

import java.io.ByteArrayInputStream

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{extractExecutionContext, extractMaterializer, extractRequestContext, onSuccess, reject}
import akka.http.scaladsl.server.{Directive1, RequestContext, StandardRoute}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import sttp.model.{Header, Part}
import sttp.tapir.internal.SeqToParams
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues}
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults}
import sttp.tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  CodecMeta,
  DecodeFailure,
  DecodeResult,
  Endpoint,
  EndpointIO,
  EndpointInput,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawPart,
  RawValueType,
  StringValueType
}

import scala.concurrent.{ExecutionContext, Future}

private[akkahttp] class EndpointToAkkaDirective(serverOptions: AkkaHttpServerOptions) {
  def apply[I, E, O](e: Endpoint[I, E, O, AkkaStream]): Directive1[I] = {
    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.server._

    val inputDirectives: Directive1[I] = {
      def decodeBody(result: DecodeInputsResult): Directive1[DecodeInputsResult] = {
        result match {
          case values: DecodeInputsResult.Values =>
            values.bodyInput match {
              case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
                rawBodyDirective(codec.meta.rawValueType)
                  .map { v =>
                    codec.decode(DecodeInputs.rawBodyValueToOption(v, codec.meta.schema.isOptional)) match {
                      case DecodeResult.Value(bodyV) => values.setBodyInputValue(bodyV)
                      case failure: DecodeFailure    => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                    }
                  }

              case None => provide(values)
            }
          case failure: DecodeInputsResult.Failure => provide(failure)
        }
      }

      extractRequestContext.flatMap { ctx =>
        decodeBody(DecodeInputs(e.input, new AkkaDecodeInputsContext(ctx))).flatMap {
          case values: DecodeInputsResult.Values          => provide(SeqToParams(InputValues(e.input, values)).asInstanceOf[I])
          case DecodeInputsResult.Failure(input, failure) => decodeFailureDirective(ctx, e, input, failure)
        }
      }
    }

    inputDirectives
  }

  private def rawBodyDirective(bodyType: RawValueType[_]): Directive1[Any] = extractRequestContext.flatMap { ctx =>
    extractMaterializer.flatMap { implicit materializer =>
      extractExecutionContext.flatMap { implicit ec =>
        onSuccess(entityToRawValue(ctx.request.entity, bodyType, ctx)).asInstanceOf[Directive1[Any]]
      }
    }
  }

  private def decodeFailureDirective[I](
      ctx: RequestContext,
      e: Endpoint[_, _, _, _],
      input: EndpointInput.Single[_],
      failure: DecodeFailure
  ): Directive1[I] = {
    val decodeFailureCtx = DecodeFailureContext(input, failure)
    val handling = serverOptions.decodeFailureHandler(decodeFailureCtx)
    handling match {
      case DecodeFailureHandling.NoMatch =>
        serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx)(ctx.log)
        reject
      case DecodeFailureHandling.RespondWithResponse(output, value) =>
        serverOptions.logRequestHandling.decodeFailureHandled(e, decodeFailureCtx, value)(ctx.log)
        StandardRoute(OutputToAkkaRoute(ServerDefaults.StatusCodes.error.code, output, value))
    }
  }

  private def entityToRawValue[R](entity: HttpEntity, rawValueType: RawValueType[R], ctx: RequestContext)(
      implicit mat: Materializer,
      ec: ExecutionContext
  ): Future[R] = {
    rawValueType match {
      case StringValueType(_)   => implicitly[FromEntityUnmarshaller[String]].apply(entity)
      case ByteArrayValueType   => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(entity)
      case ByteBufferValueType  => implicitly[FromEntityUnmarshaller[ByteString]].apply(entity).map(_.asByteBuffer)
      case InputStreamValueType => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(entity).map(new ByteArrayInputStream(_))
      case FileValueType =>
        serverOptions
          .createFile(ctx)
          .flatMap(file => entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map(_ => file))
      case mvt: MultipartValueType =>
        implicitly[FromEntityUnmarshaller[Multipart.FormData]].apply(entity).flatMap { fd =>
          fd.parts
            .mapConcat(part => mvt.partCodecMeta(part.name).map((part, _)).toList)
            .mapAsync[RawPart](1) { case (part, codecMeta) => toRawPart(part, codecMeta, ctx) }
            .runWith[Future[scala.collection.immutable.Seq[RawPart]]](Sink.seq)
            .asInstanceOf[Future[R]]
        }
    }
  }

  private def toRawPart[R](part: Multipart.FormData.BodyPart, codecMeta: CodecMeta[_, _, R], ctx: RequestContext)(
      implicit mat: Materializer,
      ec: ExecutionContext
  ): Future[Part[R]] = {
    entityToRawValue(part.entity, codecMeta.rawValueType, ctx)
      .map(r =>
        Part(
          part.name,
          r,
          otherDispositionParams = part.additionalDispositionParams,
          headers = part.additionalHeaders.map(h => Header.notValidated(h.name, h.value))
        )
      )
  }
}
