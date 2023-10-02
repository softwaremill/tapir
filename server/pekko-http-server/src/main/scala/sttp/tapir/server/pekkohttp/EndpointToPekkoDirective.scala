package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives.reject
import org.apache.pekko.http.scaladsl.server.{Directive1, RequestContext, StandardRoute}
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Sink}
import org.apache.pekko.util.ByteString
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.{Header, Part}
import sttp.monad.FutureMonad
import sttp.tapir.server.internal._
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults}
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput, RawBodyType, RawPart}

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext, Future}

private[pekkohttp] class EndpointToPekkoDirective(serverOptions: PekkoHttpServerOptions) {
  def apply[I, E, O](e: Endpoint[I, E, O, PekkoStreams with WebSockets]): Directive1[I] = {
    import org.apache.pekko.http.scaladsl.server.Directives._
    import org.apache.pekko.http.scaladsl.server._

    extractRequestContext.flatMap { ctx =>
      extractExecutionContext.flatMap { implicit ec =>
        extractMaterializer.flatMap { implicit mat =>
          val decodeBody = new DecodeBody[RequestContext, Future]()(new FutureMonad) {
            override def rawBody[R](ctx: RequestContext, body: EndpointIO.Body[R, _]): Future[R] = {
              entityToRawValue(ctx.request.entity, body.bodyType, ctx)
            }
          }

          onSuccess(decodeBody(ctx, DecodeInputs(e.input, new PekkoDecodeInputsContext(ctx)))).flatMap {
            case values: DecodeInputsResult.Values =>
              InputValues(e.input, values) match {
                case InputValuesResult.Value(params, _)        => provide(params.asAny.asInstanceOf[I])
                case InputValuesResult.Failure(input, failure) => decodeFailureDirective(ctx, e, input, failure)
              }

            case DecodeInputsResult.Failure(input, failure) => decodeFailureDirective(ctx, e, input, failure)
          }
        }
      }
    }
  }

  private def decodeFailureDirective[I](
      ctx: RequestContext,
      e: Endpoint[_, _, _, _],
      input: EndpointInput[_],
      failure: DecodeResult.Failure
  )(implicit ec: ExecutionContext, mat: Materializer): Directive1[I] = {
    val decodeFailureCtx = DecodeFailureContext(input, failure, e)
    val handling = serverOptions.decodeFailureHandler(decodeFailureCtx)
    handling match {
      case DecodeFailureHandling.NoMatch =>
        serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx)(ctx.log)
        reject
      case DecodeFailureHandling.RespondWithResponse(output, value) =>
        serverOptions.logRequestHandling.decodeFailureHandled(e, decodeFailureCtx, value)(ctx.log)
        StandardRoute(new OutputToPekkoRoute().apply(ServerDefaults.StatusCodes.error.code, output, value))
    }
  }

  private def entityToRawValue[R](
      entity: HttpEntity,
      bodyType: RawBodyType[R],
      ctx: RequestContext
  )(implicit
      mat: Materializer,
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

  private def toRawPart[R](part: Multipart.FormData.BodyPart, bodyType: RawBodyType[R], ctx: RequestContext)(implicit
      mat: Materializer,
      ec: ExecutionContext
  ): Future[Part[R]] = {
    entityToRawValue(part.entity, bodyType, ctx)
      .map(r =>
        Part(
          part.name,
          r,
          otherDispositionParams = part.additionalDispositionParams,
          headers = part.additionalHeaders.map(h => Header(h.name, h.value))
        ).contentType(part.entity.contentType.toString())
      )
  }
}
