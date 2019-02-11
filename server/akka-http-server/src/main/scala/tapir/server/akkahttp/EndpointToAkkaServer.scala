package tapir.server.akkahttp

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{
  BodyPartEntity,
  ContentType,
  ContentTypes,
  HttpCharset,
  HttpEntity,
  HttpHeader,
  HttpMethod,
  HttpResponse,
  MediaTypes,
  Multipart,
  ResponseEntity,
  StatusCode => AkkaStatusCode,
  MediaType => _
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import tapir._
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.server.DecodeFailureHandling
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class EndpointToAkkaServer(serverOptions: AkkaHttpServerOptions) {
  def toDirective[I, E, O, T](e: Endpoint[I, E, O, AkkaStream])(implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(paramsToTuple.toTuple(values))
    }
  }

  def toRoute[I, E, O, FN[_]](e: Endpoint[I, E, O, AkkaStream])(
      logic: FN[Future[Either[E, O]]],
      statusMapper: O => StatusCode,
      errorStatusMapper: E => StatusCode)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Route = {
    toDirective1(e) { values =>
      onSuccess(paramsAsArgs.applyFn(logic, values)) {
        case Left(v)  => outputToRoute(errorStatusMapper(v), e.errorOutput, v)
        case Right(v) => outputToRoute(statusMapper(v), e.output, v)
      }
    }
  }

  // don't look below. The code is really, really ugly. Even worse than in EndpointToSttpClient

  private def outputToRoute[O](statusCode: AkkaStatusCode, output: EndpointIO[O], v: O): Route = {
    val outputValues = singleOutputsWithValues(output.asVectorOfSingle, v, OutputValues(None, Vector.empty))

    val completeRoute = outputValues.body match {
      case Some(entity) => complete(HttpResponse(entity = entity, status = statusCode))
      case None         => complete(HttpResponse(statusCode))
    }

    if (outputValues.headers.nonEmpty) {
      respondWithHeaders(outputValues.headers: _*)(completeRoute)
    } else {
      completeRoute
    }
  }

  private case class OutputValues(body: Option[ResponseEntity], headers: Vector[HttpHeader])

  private def singleOutputsWithValues(outputs: Vector[EndpointIO.Single[_]], v: Any, initialOutputValues: OutputValues): OutputValues = {
    val vs = ParamsToSeq(v)
    var ov = initialOutputValues

    outputs.zipWithIndex.foreach {
      case (EndpointIO.Body(codec, _), i) =>
        // TODO: check if body isn't already set
        codec.encode(vs(i)).map(rawValueToResponseEntity(codec.meta, _)).foreach(re => ov = ov.copy(body = Some(re)))
      case (EndpointIO.StreamBodyWrapper(StreamingEndpointIO.Body(_, mediaType, _)), i) =>
        // TODO: check if body isn't already set
        val re = HttpEntity(mediaTypeToContentType(mediaType), vs(i).asInstanceOf[AkkaStream])
        ov = ov.copy(body = Some(re))
      case (EndpointIO.Header(name, codec, _), i) =>
        codec
          .encode(vs(i))
          .map(HttpHeader.parse(name, _))
          .collect {
            case ParsingResult.Ok(h, _) => h
            // TODO error on parse error?
          }
          .foreach(hh => ov = ov.copy(headers = ov.headers :+ hh))
      case (EndpointIO.Headers(_), i) =>
        vs(i)
          .asInstanceOf[Seq[(String, String)]]
          .map(h => HttpHeader.parse(h._1, h._2))
          .collect {
            case ParsingResult.Ok(h, _) => h
            // TODO error on parse error?
          }
          .foreach(h => ov = ov.copy(headers = ov.headers :+ h))
      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        ov = singleOutputsWithValues(wrapped.asVectorOfSingle, g(vs(i)), ov)
    }

    ov
  }

  private def toDirective1[I, E, O](e: Endpoint[I, E, O, AkkaStream]): Directive1[I] = {

    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.server._

    val methodDirective = methodToAkkaDirective(e)

    val inputDirectives: Directive1[I] = {

      def rawBodyDirective(bodyType: RawValueType[_]): Directive1[Any] = extractRequestContext.flatMap { ctx =>
        extractMaterializer.flatMap { implicit materializer =>
          extractExecutionContext.flatMap { implicit ec =>
            onSuccess(entityToRawValue(ctx.request.entity, bodyType, ctx)).asInstanceOf[Directive1[Any]]
          }
        }
      }

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
          case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(ctx, input, failure)
        }
      }
    }

    methodDirective & inputDirectives
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

  private def methodToAkkaDirective[O, E, I](e: Endpoint[I, E, O, AkkaStream]) = {
    e.method match {
      case Method.GET     => get
      case Method.HEAD    => head
      case Method.POST    => post
      case Method.PUT     => put
      case Method.DELETE  => delete
      case Method.OPTIONS => options
      case Method.PATCH   => patch
      case m              => method(HttpMethod.custom(m.m))
    }
  }

  private def rawValueToResponseEntity[M <: MediaType, R](codecMeta: CodecMeta[M, R], r: R): ResponseEntity = {
    val ct = mediaTypeToContentType(codecMeta.mediaType)
    codecMeta.rawValueType match {
      case StringValueType(charset) =>
        ct match {
          case nb: ContentType.NonBinary => HttpEntity(nb, r)
          case _                         => HttpEntity(ct, r.getBytes(charset))
        }
      case ByteArrayValueType   => HttpEntity(ct, r)
      case ByteBufferValueType  => HttpEntity(ct, ByteString(r))
      case InputStreamValueType => HttpEntity(ct, StreamConverters.fromInputStream(() => r))
      case FileValueType        => HttpEntity.fromPath(ct, r.toPath)
      case mvt: MultipartValueType =>
        val parts = (r: Seq[RawPart]).flatMap(rawPartToBodyPart(mvt, _))
        val body = Multipart.FormData(parts: _*)
        body.toEntity()
    }
  }

  private def rawPartToBodyPart[T](mvt: MultipartValueType, part: Part[T]): Option[Multipart.FormData.BodyPart] = {
    mvt.partCodecMeta(part.name).map { codecMeta =>
      val headers = part.headers.map {
        case (hk, hv) =>
          HttpHeader.parse(hk, hv) match {
            case ParsingResult.Ok(h, _) => h
            case _                      => throw new IllegalArgumentException(s"Invalid header: $hk -> $hv") // TODO
          }
      }

      val body = rawValueToResponseEntity(codecMeta.asInstanceOf[CodecMeta[_ <: MediaType, Any]], part.body) match {
        case b: BodyPartEntity => b
        case _                 => throw new IllegalArgumentException(s"${codecMeta.rawValueType} is not supported in multipart bodies")
      }

      Multipart.FormData.BodyPart(part.name, body, part.otherDispositionParams, headers.toList)
    }
  }

  private def mediaTypeToContentType(mediaType: MediaType): ContentType = {
    mediaType match {
      case MediaType.Json()               => ContentTypes.`application/json`
      case MediaType.TextPlain(charset)   => MediaTypes.`text/plain`.withCharset(charsetToHttpCharset(charset))
      case MediaType.OctetStream()        => MediaTypes.`application/octet-stream`
      case MediaType.XWwwFormUrlencoded() => MediaTypes.`application/x-www-form-urlencoded`.withMissingCharset
      case MediaType.MultipartFormData()  => MediaTypes.`multipart/form-data`
      case mt                             => ContentType.parse(mt.mediaType).right.get
    }
  }

  private def charsetToHttpCharset(charset: Charset): HttpCharset = HttpCharset.custom(charset.name())

  private def handleDecodeFailure[I](ctx: RequestContext, input: EndpointInput.Single[_], failure: DecodeFailure): Directive1[I] = {
    val handling = serverOptions.decodeFailureHandler(ctx, input, failure)
    handling match {
      case DecodeFailureHandling.NoMatch => reject
      case DecodeFailureHandling.RespondWithResponse(statusCode, body, codec) =>
        complete(HttpResponse(entity = rawValueToResponseEntity(codec.meta, codec.encode(body)), status = statusCode))
    }
  }
}
