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
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues, InputValuesResult}
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults}
import sttp.tapir.{RawBodyType, DecodeResult, Endpoint, EndpointIO, EndpointInput, RawPart}

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
              case Some(bodyInput @ EndpointIO.Body(bodyType, codec, _)) =>
                rawBodyDirective(bodyType)
                  .map { v =>
                    codec.decode(v) match {
                      case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
                      case failure: DecodeResult.Failure => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                    }
                  }

              case None => provide(values)
            }
          case failure: DecodeInputsResult.Failure => provide(failure)
        }
      }

      extractRequestContext.flatMap { ctx =>
        decodeBody(DecodeInputs(e.input, new AkkaDecodeInputsContext(ctx))).flatMap {
          case values: DecodeInputsResult.Values =>
            InputValues(e.input, values) match {
              case InputValuesResult.Values(values, _)       => provide(SeqToParams(values).asInstanceOf[I])
              case InputValuesResult.Failure(input, failure) => decodeFailureDirective(ctx, e, input, failure)
            }

          case DecodeInputsResult.Failure(input, failure) => decodeFailureDirective(ctx, e, input, failure)
        }
      }
    }

    inputDirectives
  }

  private def rawBodyDirective(bodyType: RawBodyType[_]): Directive1[Any] = extractRequestContext.flatMap { ctx =>
    extractMaterializer.flatMap { implicit materializer =>
      extractExecutionContext.flatMap { implicit ec =>
        onSuccess(entityToRawValue(ctx.request.entity, bodyType, ctx)).asInstanceOf[Directive1[Any]]
      }
    }
  }

  private def decodeFailureDirective[I](
      ctx: RequestContext,
      e: Endpoint[_, _, _, _],
      input: EndpointInput[_],
      failure: DecodeResult.Failure
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

  private def entityToRawValue[R](
      entity: HttpEntity,
      bodyType: RawBodyType[R],
      ctx: RequestContext
  )(
      implicit mat: Materializer,
      ec: ExecutionContext
  ): Future[R] = {
    bodyType match {
      case RawBodyType.StringBody(_)   => implicitly[FromEntityUnmarshaller[String]].apply(entity)
      case RawBodyType.ByteArrayBody   => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(entity)
      case RawBodyType.ByteBufferBody  => implicitly[FromEntityUnmarshaller[ByteString]].apply(entity).map(_.asByteBuffer)
      case RawBodyType.InputStreamBody => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(entity).map(new ByteArrayInputStream(_))
      case RawBodyType.FileBody =>
        serverOptions
          .createFile(ctx)
          .flatMap(file => entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map(_ => file))
      case m: RawBodyType.MultipartBody =>
        implicitly[FromEntityUnmarshaller[Multipart.FormData]].apply(entity).flatMap { fd =>
          fd.parts
            .mapConcat(part => m.partType(part.name).map((part, _)).toList)
            .mapAsync[RawPart](1) { case (part, codecMeta) => toRawPart(part, codecMeta, ctx) }
            .runWith[Future[scala.collection.immutable.Seq[RawPart]]](Sink.seq)
            .asInstanceOf[Future[R]]
        }
    }
  }

  private def toRawPart[R](part: Multipart.FormData.BodyPart, bodyType: RawBodyType[R], ctx: RequestContext)(
      implicit mat: Materializer,
      ec: ExecutionContext
  ): Future[Part[R]] = {
    entityToRawValue(part.entity, bodyType, ctx)
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
