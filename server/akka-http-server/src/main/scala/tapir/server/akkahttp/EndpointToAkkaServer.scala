package tapir.server.akkahttp

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{MediaType => _, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import tapir._
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}

import scala.concurrent.Future

object EndpointToAkkaServer {

  def toDirective[I, E, O, T](e: Endpoint[I, E, O])(implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(paramsToTuple.toTuple(values))
    }
  }

  def toRoute[I, E, O, FN[_]](e: Endpoint[I, E, O])(logic: FN[Future[Either[E, O]]])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Route = {
    toDirective1(e) { values =>
      onSuccess(paramsAsArgs.applyFn(logic, values)) {
        case Left(v)  => outputToRoute(StatusCodes.BadRequest, e.errorOutput, v)
        case Right(v) => outputToRoute(StatusCodes.OK, e.output, v)
      }
    }
  }

  // don't look below. The code is really, really ugly. Even worse than in EndpointToSttpClient

  private def singleOutputsWithValues(outputs: Vector[EndpointIO.Single[_]], v: Any): Vector[Either[ResponseEntity, HttpHeader]] = {
    val vs = ParamsToSeq(v)

    outputs.zipWithIndex.flatMap {
      case (EndpointIO.Body(codec, _, _), i) =>
        encodeBody(vs(i), codec).map(Left(_))
      case (EndpointIO.Header(name, codec, _, _), i) =>
        codec
          .encodeOptional(vs(i))
          .map(HttpHeader.parse(name, _))
          .collect {
            case ParsingResult.Ok(h, _) => h
            // TODO error on parse error?
          }
          .map(Right(_))
      case (EndpointIO.Mapped(wrapped, _, g, _), i) =>
        singleOutputsWithValues(wrapped.asVectorOfSingle, g(vs(i)))
    }
  }

  private def outputToRoute[O](statusCode: StatusCode, output: EndpointIO[O], v: O): Route = {
    val outputsWithValues = singleOutputsWithValues(output.asVectorOfSingle, v)

    val body = outputsWithValues.collectFirst { case Left(b) => b }
    val headers = outputsWithValues.collect { case Right(h)  => h }

    val completeRoute = body.map(entity => complete(HttpResponse(entity = entity, status = statusCode))).getOrElse(complete(""))

    if (headers.nonEmpty) {
      respondWithHeaders(headers: _*)(completeRoute)
    } else {
      completeRoute
    }
  }

  private def toDirective1[I, E, O](e: Endpoint[I, E, O]): Directive1[I] = {

    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.server._

    val methodDirective = e.method match {
      case Method.GET     => get
      case Method.HEAD    => head
      case Method.POST    => post
      case Method.PUT     => put
      case Method.DELETE  => delete
      case Method.OPTIONS => options
      case Method.PATCH   => patch
      case m              => method(HttpMethod.custom(m.m))
    }

    // TODO: when parsing a query parameter/header/body/path fragment fails, provide an option to return a nice
    // error to the user (instead of a 404).

    case class MatchResult(values: List[Any], ctx: RequestContext, canRemoveSlash: Boolean) {
      def prependValue(v: Any): MatchResult = copy(values = v :: values)
    }

    def doMatch(inputs: Vector[EndpointInput.Single[_]], ctx: RequestContext, canRemoveSlash: Boolean, body: Any): Option[MatchResult] = {

      def handleMapped[II, T](wrapped: EndpointInput[II], f: II => T, inputsTail: Vector[EndpointInput.Single[_]]): Option[MatchResult] = {
        doMatch(wrapped.asVectorOfSingle, ctx, canRemoveSlash, body).flatMap { result =>
          doMatch(inputsTail, result.ctx, result.canRemoveSlash, body).map {
            _.prependValue(f.asInstanceOf[Any => Any].apply(SeqToParams(result.values)))
          }
        }
      }

      inputs match {
        case Vector() => Some(MatchResult(Nil, ctx, canRemoveSlash))
        case EndpointInput.PathSegment(ss) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash =>
              doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false, body)
            case Uri.Path.Segment(`ss`, pathTail) =>
              doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true, body)
            case _ => None
          }
        case EndpointInput.PathCapture(codec, _, _, _) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash =>
              doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false, body)
            case Uri.Path.Segment(s, pathTail) =>
              codec.decode(s) match {
                case DecodeResult.Value(v) =>
                  doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true, body).map {
                    _.prependValue(v)
                  }
                case _ => None
              }
            case _ => None
          }
        case EndpointInput.Query(name, codec, _, _) +: inputsTail =>
          codec.decodeOptional(ctx.request.uri.query().get(name)) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
                _.prependValue(v)
              }
            case _ => None
          }
        case EndpointIO.Header(name, codec, _, _) +: inputsTail =>
          codec.decodeOptional(ctx.request.headers.find(_.is(name.toLowerCase)).map(_.value())) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
                _.prependValue(v)
              }
            case _ => None
          }
        case EndpointIO.Body(codec, _, _) +: inputsTail =>
          codec.decodeOptional(Some(body)) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
                _.prependValue(v)
              }
            case _ => None
          }
        case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
          handleMapped(wrapped, f, inputsTail)
        case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
          handleMapped(wrapped, f, inputsTail)
      }

    }

    val inputDirectives: Directive1[I] = {
      val bodyDirective: Directive1[Any] = e.input.bodyType match {
        case Some(StringValueType(_))   => entity(as[String]).asInstanceOf[Directive1[Any]]
        case Some(ByteArrayValueType)   => entity(as[Array[Byte]]).asInstanceOf[Directive1[Any]]
        case Some(ByteBufferValueType)  => entity(as[ByteString]).map(_.asByteBuffer).asInstanceOf[Directive1[Any]]
        case Some(InputStreamValueType) => entity(as[Array[Byte]]).map(new ByteArrayInputStream(_)).asInstanceOf[Directive1[Any]]
        case None                       => provide(null)
      }
      bodyDirective.flatMap { body =>
        extractRequestContext.flatMap { ctx =>
          doMatch(e.input.asVectorOfSingle, ctx, canRemoveSlash = true, body) match {
            case Some(result) =>
              provide(SeqToParams(result.values).asInstanceOf[I]) & mapRequestContext(_ => result.ctx)
            case None => reject
          }
        }
      }
    }

    methodDirective & inputDirectives
  }

  private def encodeBody[T, M <: MediaType, R](v: T, codec: GeneralCodec[T, M, R]): Option[ResponseEntity] = {
    val ct = mediaTypeToContentType(codec.mediaType)
    codec.encodeOptional(v).map { r =>
      codec.rawValueType.fold(r)(
        (s, charset) =>
          ct match {
            case nonBinary: ContentType.NonBinary => HttpEntity(nonBinary, s)
            case _                                => HttpEntity(ct, s.getBytes(charset))
        },
        b => HttpEntity(ct, b),
        b => HttpEntity(ct, ByteString(b)),
        b => HttpEntity(ct, StreamConverters.fromInputStream(() => b))
      )
    }
  }

  private def mediaTypeToContentType(mediaType: MediaType): ContentType = {
    mediaType match {
      case MediaType.Json()             => ContentTypes.`application/json`
      case MediaType.TextPlain(charset) => MediaTypes.`text/plain`.withCharset(charsetToHttpCharset(charset))
      case MediaType.OctetStream()      => ContentTypes.`application/octet-stream`
      case mt                           => ContentType.parse(mt.mediaType).right.get
    }
  }

  private def charsetToHttpCharset(charset: Charset): HttpCharset = HttpCharset.custom(charset.name())
}
