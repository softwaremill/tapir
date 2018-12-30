package tapir.server.akkahttp

import java.nio.charset.Charset

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{MediaType => _, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, RequestContext, Route}
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

    val methodDirective = methodDirectiveType(e)

    // TODO: when parsing a query parameter/header/body/path fragment fails, provide an option to return a nice
    // error to the user (instead of a 404).

    val inputDirectives: Directive1[I] = {
      val bodyDirective: Directive1[Any] = bodyType(e.input) match {
        case Some(StringValueType(_)) => entity(as[String]).asInstanceOf[Directive1[Any]]
        case Some(ByteArrayValueType) => entity(as[Array[Byte]]).asInstanceOf[Directive1[Any]]
        case _                        => provide(null)
      }
      bodyDirective.flatMap { body =>
        extractRequestContext.flatMap { ctx =>
          AkkaHttpInputMatcher.doMatch(e.input.asVectorOfSingle, ctx, body) match {
            case Some(result) =>
              provide(SeqToParams(result.values).asInstanceOf[I]) & mapRequestContext(_ => result.ctx)
            case None => reject
          }
        }
      }
    }

    methodDirective & inputDirectives
  }

  private def methodDirectiveType[O, E, I](e: Endpoint[I, E, O]) = {
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

  private def bodyType[I](in: EndpointInput[I]): Option[RawValueType[_]] = {
    in match {
      case b: EndpointIO.Body[_, _, _]            => Some(b.codec.rawValueType)
      case EndpointInput.Multiple(inputs)         => inputs.flatMap(bodyType(_)).headOption
      case EndpointIO.Multiple(inputs)            => inputs.flatMap(bodyType(_)).headOption
      case EndpointInput.Mapped(wrapped, _, _, _) => bodyType(wrapped)
      case EndpointIO.Mapped(wrapped, _, _, _)    => bodyType(wrapped)
      case _                                      => None
    }
  }

  private def encodeBody[T, M <: MediaType, R](v: T, codec: GeneralCodec[T, M, R]): Option[ResponseEntity] = {
    val ct = mediaTypeToContentType(codec.mediaType)
    codec.encodeOptional(v).map { r =>
      codec.rawValueType.fold(r)((s, charset) =>
                                   ct match {
                                     case nonBinary: ContentType.NonBinary => HttpEntity(nonBinary, s)
                                     case _                                => HttpEntity(ct, s.getBytes(charset))
                                 },
                                 b => HttpEntity(ct, b))
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
