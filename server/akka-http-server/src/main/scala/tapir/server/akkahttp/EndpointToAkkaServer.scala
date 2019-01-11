package tapir.server.akkahttp

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.{Charset, StandardCharsets}

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{
  ContentType,
  ContentTypes,
  FormData,
  HttpCharset,
  HttpEntity,
  HttpHeader,
  HttpMethod,
  HttpResponse,
  MediaTypes,
  ResponseEntity,
  StatusCode => AkkaStatusCode,
  MediaType => _
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import tapir._
import tapir.internal.{ParamsToSeq, SeqToParams, UrlencodedData}
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}

import scala.concurrent.Future
import scala.util.Failure

class EndpointToAkkaServer(serverOptions: AkkaHttpServerOptions) {
  def toDirective[I, E, O, T](e: Endpoint[I, E, O])(implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(paramsToTuple.toTuple(values))
    }
  }

  def toRoute[I, E, O, FN[_]](e: Endpoint[I, E, O])(
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
    val outputValues = singleOutputsWithValues(output.asVectorOfSingle, v, OutputValues(None, Vector.empty, Vector.empty))

    val completeRoute = (outputValues.body, outputValues.formParams) match {
      case (Some(_), form) if form.nonEmpty =>
        throw new IllegalStateException("endpoint can't have both form data parameters and a body output")
      case (None, form) if form.nonEmpty => complete(HttpResponse(entity = FormData(form: _*).toEntity, status = statusCode))
      case (Some(entity), _)             => complete(HttpResponse(entity = entity, status = statusCode))
      case _                             => complete(HttpResponse(statusCode))
    }

    if (outputValues.headers.nonEmpty) {
      respondWithHeaders(outputValues.headers: _*)(completeRoute)
    } else {
      completeRoute
    }
  }

  private case class OutputValues(body: Option[ResponseEntity], headers: Vector[HttpHeader], formParams: Vector[(String, String)])

  private def singleOutputsWithValues(outputs: Vector[EndpointIO.Single[_]], v: Any, initialOutputValues: OutputValues): OutputValues = {
    val vs = ParamsToSeq(v)
    var ov = initialOutputValues

    outputs.zipWithIndex.foreach {
      case (EndpointIO.Body(codec, _, _), i) =>
        // TODO: check if body isn't already set
        encodeBody(vs(i), codec).foreach(re => ov = ov.copy(body = Some(re)))
      case (EndpointIO.Header(name, codec, _, _), i) =>
        codec
          .encodeOptional(vs(i))
          .map(HttpHeader.parse(name, _))
          .collect {
            case ParsingResult.Ok(h, _) => h
            // TODO error on parse error?
          }
          .foreach(hh => ov = ov.copy(headers = ov.headers :+ hh))
      case (EndpointIO.Form(name, codec, _, _), i) =>
        codec
          .encodeOptional(vs(i))
          .map(name -> _)
          .foreach(d => ov = ov.copy(formParams = ov.formParams :+ d))
      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        ov = singleOutputsWithValues(wrapped.asVectorOfSingle, g(vs(i)), ov)
    }

    ov
  }

  private def toDirective1[I, E, O](e: Endpoint[I, E, O]): Directive1[I] = {

    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.server._

    val methodDirective = methodToAkkaDirective(e)

    // TODO: when parsing a query parameter/header/body/path fragment fails, provide an option to return a nice
    // error to the user (instead of a 404).

    val inputDirectives: Directive1[I] = {
      val bodyType = e.input.bodyType
      val bodyDirective: Directive1[Any] = bodyType match {
        case Some(StringValueType(_))   => entity(as[String]).asInstanceOf[Directive1[Any]]
        case Some(ByteArrayValueType)   => entity(as[Array[Byte]]).asInstanceOf[Directive1[Any]]
        case Some(ByteBufferValueType)  => entity(as[ByteString]).map(_.asByteBuffer).asInstanceOf[Directive1[Any]]
        case Some(InputStreamValueType) => entity(as[Array[Byte]]).map(new ByteArrayInputStream(_)).asInstanceOf[Directive1[Any]]
        case Some(FileValueType)        => saveRequestBodyToFile.asInstanceOf[Directive1[Any]]
        case None                       => provide(null)
      }
      val formDataDirective = formData(e, bodyType, bodyDirective)
      bodyDirective.flatMap { body =>
        formDataDirective.flatMap { formData =>
          extractRequestContext.flatMap { ctx =>
            AkkaHttpInputMatcher.doMatch(e.input.asVectorOfSingle, ctx, body, formData) match {
              case Some(result) =>
                provide(SeqToParams(result).asInstanceOf[I]) & mapRequestContext(_ => ctx)
              case None => reject
            }
          }
        }
      }
    }

    methodDirective & inputDirectives
  }

  private def formData[I, E, O](e: Endpoint[I, E, O],
                                bodyType: Option[RawValueType[_]],
                                bodyDirective: Directive1[Any]): Directive1[Seq[(String, String)]] = {
    if (e.input.hasForm) {
      val bodyAsStringDirective = bodyType match {
        case Some(StringValueType(_)) =>
          bodyDirective.asInstanceOf[Directive1[String]]
        case Some(_) => throw new IllegalStateException("form data parameters can only be read if the body is read as a string")
        case None =>
          entity(as[String])
      }

      bodyAsStringDirective.map(UrlencodedData.decode(_, StandardCharsets.UTF_8)) // TODO
    } else provide(Nil)
  }

  private def saveRequestBodyToFile: Directive1[File] = {
    extractRequestContext.flatMap { r =>
      onSuccess(serverOptions.createFile(r)).flatMap { file =>
        extractMaterializer.flatMap { implicit materializer =>
          val runResult: Future[IOResult] = r.request.entity.dataBytes.runWith(FileIO.toPath(file.toPath))
          onSuccess(runResult).map { ioResult =>
            ioResult.status match {
              case Failure(t) => throw t
              case _          => // do nothing
            }
            file
          }
        }
      }
    }
  }

  private def methodToAkkaDirective[O, E, I](e: Endpoint[I, E, O]) = {
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

  private def encodeBody[T, M <: MediaType, R](v: T, codec: GeneralCodec[T, M, R]): Option[ResponseEntity] = {
    val ct = mediaTypeToContentType(codec.mediaType)
    codec.encodeOptional(v).map { r =>
      codec.rawValueType match {
        case StringValueType(charset) =>
          ct match {
            case nonBinary: ContentType.NonBinary => HttpEntity(nonBinary, r)
            case _                                => HttpEntity(ct, r.getBytes(charset))
          }
        case ByteArrayValueType   => HttpEntity(ct, r)
        case ByteBufferValueType  => HttpEntity(ct, ByteString(r))
        case InputStreamValueType => HttpEntity(ct, StreamConverters.fromInputStream(() => r))
        case FileValueType        => HttpEntity(ct, FileIO.fromPath(r.toPath))
      }
    }
  }

  private def mediaTypeToContentType(mediaType: MediaType): ContentType = {
    mediaType match {
      case MediaType.Json()               => ContentTypes.`application/json`
      case MediaType.TextPlain(charset)   => MediaTypes.`text/plain`.withCharset(charsetToHttpCharset(charset))
      case MediaType.OctetStream()        => MediaTypes.`application/octet-stream`
      case MediaType.XWwwFormUrlencoded() => MediaTypes.`application/x-www-form-urlencoded`.withMissingCharset
      case mt                             => ContentType.parse(mt.mediaType).right.get
    }
  }

  private def charsetToHttpCharset(charset: Charset): HttpCharset = HttpCharset.custom(charset.name())
}
