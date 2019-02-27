package tapir.server.akkahttp
import java.io.ByteArrayInputStream

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{
  complete,
  delete,
  extractExecutionContext,
  extractMaterializer,
  extractRequestContext,
  get,
  head,
  method,
  onSuccess,
  options,
  patch,
  post,
  put,
  reject,
  pass
}
import akka.http.scaladsl.server.{Directive0, Directive1, RequestContext}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import tapir.internal.SeqToParams
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.model.{Method, Part}
import tapir.server.DecodeFailureHandling
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

    val methodDirective = methodToAkkaDirective(e)

    val inputDirectives: Directive1[I] = {

      def decodeBody(result: DecodeInputsResult): Directive1[DecodeInputsResult] = {
        result match {
          case values: DecodeInputsResult.Values =>
            values.bodyInput match {
              case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
                rawBodyDirective(codec.meta.rawValueType)
                  .map { v =>
                    codec.decode(Some(v)) match {
                      case DecodeResult.Value(bodyV) => values.value(bodyInput, bodyV)
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
          case DecodeInputsResult.Values(values, _)       => provide(SeqToParams(InputValues(e.input, values)).asInstanceOf[I])
          case DecodeInputsResult.Failure(input, failure) => decodeFailureDirective(ctx, input, failure)
        }
      }
    }

    methodDirective & inputDirectives
  }

  private def methodToAkkaDirective[O, E, I](e: Endpoint[I, E, O, AkkaStream]): Directive0 = {
    e.method match {
      case Some(m) =>
        m match {
          case Method.GET     => get
          case Method.HEAD    => head
          case Method.POST    => post
          case Method.PUT     => put
          case Method.DELETE  => delete
          case Method.OPTIONS => options
          case Method.PATCH   => patch
          case _              => method(HttpMethod.custom(m.m))
        }

      case None => pass
    }
  }

  private def rawBodyDirective(bodyType: RawValueType[_]): Directive1[Any] = extractRequestContext.flatMap { ctx =>
    extractMaterializer.flatMap { implicit materializer =>
      extractExecutionContext.flatMap { implicit ec =>
        onSuccess(entityToRawValue(ctx.request.entity, bodyType, ctx)).asInstanceOf[Directive1[Any]]
      }
    }
  }

  private def decodeFailureDirective[I](ctx: RequestContext, input: EndpointInput.Single[_], failure: DecodeFailure): Directive1[I] = {
    val handling = serverOptions.decodeFailureHandler(ctx, input, failure)
    handling match {
      case DecodeFailureHandling.NoMatch => reject
      case DecodeFailureHandling.RespondWithResponse(statusCode, body, codec) =>
        complete(HttpResponse(entity = OutputToAkkaResponse.rawValueToResponseEntity(codec.meta, codec.encode(body)), status = statusCode))
    }
  }

  private def entityToRawValue[R](entity: HttpEntity, rawValueType: RawValueType[R], ctx: RequestContext)(
      implicit mat: Materializer,
      ec: ExecutionContext): Future[R] = {

    rawValueType match {
      case StringValueType(_)   => implicitly[FromEntityUnmarshaller[String]].apply(entity)
      case ByteArrayValueType   => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(entity)
      case ByteBufferValueType  => implicitly[FromEntityUnmarshaller[ByteString]].apply(entity).map(_.asByteBuffer)
      case InputStreamValueType => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(entity).map(new ByteArrayInputStream(_))
      case FileValueType =>
        serverOptions
          .createFile(ctx)
          .flatMap(file =>
            entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map { ioResult =>
              ioResult.status match {
                case Failure(t) => throw t
                case _          => // do nothing
              }
              file
          })
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
      ec: ExecutionContext): Future[Part[R]] = {

    entityToRawValue(part.entity, codecMeta.rawValueType, ctx)
      .map(r => Part(part.name, part.additionalDispositionParams, part.headers.map(h => (h.name, h.value)), r))
  }
}
