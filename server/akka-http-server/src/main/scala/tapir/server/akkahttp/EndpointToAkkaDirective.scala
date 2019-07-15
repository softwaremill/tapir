package tapir.server.akkahttp

import java.io.ByteArrayInputStream

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{
  extractExecutionContext,
  extractMaterializer,
  extractRequestContext,
  onSuccess,
  reject,
  complete
}
import akka.http.scaladsl.model.{StatusCodes => AkkaStatusCodes}
import akka.http.scaladsl.server.{Directive1, RequestContext, StandardRoute}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import tapir.internal.SeqToParams
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.model.Part
import tapir.server.akkahttp.EndpointToAkkaDirective.TapirRejectionHandling
import tapir.server.{DecodeFailureHandler, DecodeFailureHandling, ServerDefaults}
import tapir.{
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
import scala.util.Failure

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
                    codec.safeDecode(DecodeInputs.rawBodyValueToOption(v, codec.meta.isOptional)) match {
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
    val handling: DecodeFailureHandling = serverOptions.decodeFailureHandler(ctx, input, failure)
    handling match {
      case DecodeFailureHandling.NoMatch =>
        serverOptions.loggingOptions.decodeFailureNotHandledMsg(e, failure, input).foreach(ctx.log.debug)
        reject
      case TapirRejectionHandling(route) =>
        route
      case DecodeFailureHandling.RespondWithResponse(output, value) =>
        serverOptions.loggingOptions.decodeFailureHandledMsg(e, failure, input, value).foreach {
          case (msg, Some(t)) => ctx.log.debug(s"$msg; exception: {}", t)
          case (msg, None)    => ctx.log.debug(msg)
        }
        StandardRoute(OutputToAkkaRoute(ServerDefaults.errorStatusCode, output, value))
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
          .flatMap(
            file =>
              entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map { ioResult =>
                ioResult.status match {
                  case Failure(t) => throw t
                  case _          => // do nothing
                }
                file
              }
          )
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

  private def toRawPart[R](part: Multipart.FormData.BodyPart, codecMeta: CodecMeta[_, R], ctx: RequestContext)(
      implicit mat: Materializer,
      ec: ExecutionContext
  ): Future[Part[R]] = {

    entityToRawValue(part.entity, codecMeta.rawValueType, ctx)
      .map(r => Part(part.name, part.additionalDispositionParams, part.headers.map(h => (h.name, h.value)), r))
  }
}

object EndpointToAkkaDirective {

  case class TapirRejectionHandling(route: StandardRoute) extends DecodeFailureHandling

  private implicit def routeToTapirRejectionHandling(route: StandardRoute): TapirRejectionHandling = TapirRejectionHandling(route)

  def tapirRejectionHandler: DecodeFailureHandler[RequestContext] =
    (_, input, _) => {
      input match {
        case EndpointInput.Query(name, _, _) =>
          complete(
            HttpResponse(
              AkkaStatusCodes.BadRequest,
              entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Invalid value for: query parameter $name")
            )
          )
        case _: EndpointInput.QueryParams =>
          complete(
            HttpResponse(
              AkkaStatusCodes.BadRequest,
              entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid value for: query parameters")
            )
          )
        case EndpointInput.Cookie(name, _, _) =>
          complete(
            HttpResponse(
              AkkaStatusCodes.BadRequest,
              entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Invalid value for: cookie $name")
            )
          )
        case EndpointIO.Header(name, _, _) =>
          complete(
            HttpResponse(
              AkkaStatusCodes.BadRequest,
              entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Invalid value for: header $name")
            )
          )
        case _: EndpointIO.Headers =>
          complete(
            HttpResponse(AkkaStatusCodes.BadRequest, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Invalid value for: headers"))
          )
        case _: EndpointIO.Body[_, _, _] =>
          complete(
            HttpResponse(AkkaStatusCodes.BadRequest, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Invalid value for: body"))
          )
        case _: EndpointIO.StreamBodyWrapper[_, _] =>
          complete(
            HttpResponse(AkkaStatusCodes.BadRequest, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Invalid value for: body"))
          )
        case _ =>
          DecodeFailureHandling.noMatch
      }
    }
}
