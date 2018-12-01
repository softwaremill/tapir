package tapir.server.akkahttp

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpHeader, HttpMethod, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, Route}
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
        case Left(v)  => outputToRoute(e.errorOutput, v)
        case Right(v) => outputToRoute(e.output, v)
      }
    }
  }

  // don't look below. The code is really, really ugly. Even worse than in EndpointToSttpClient

  private def singleOutputsWithValues(outputs: Vector[EndpointIO.Single[_]], v: Any): Vector[Either[String, HttpHeader]] = {
    val vs = ParamsToSeq(v)

    outputs.zipWithIndex.flatMap {
      case (EndpointIO.Body(m, _, _), i) => m.toOptionalString(vs(i)).map(Left(_))
      case (EndpointIO.Header(name, m, _, _), i) =>
        m.toOptionalString(vs(i))
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

  private def outputToRoute[O](output: EndpointIO[O], v: O): Route = {
    val outputsWithValues = singleOutputsWithValues(output.asVectorOfSingle, v)

    val body = outputsWithValues.collectFirst { case Left(b) => b }
    val headers = outputsWithValues.collect { case Right(h)  => h }

    val completeRoute = body.map(complete(_)).getOrElse(complete(""))

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

    def doMatch(inputs: Vector[EndpointInput.Single[_]],
                ctx: RequestContext,
                canRemoveSlash: Boolean,
                body: String): Option[MatchResult] = {
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
        case EndpointInput.PathCapture(m, _, _, _) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash =>
              doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false, body)
            case Uri.Path.Segment(s, pathTail) =>
              m.fromString(s) match {
                case DecodeResult.Value(v) =>
                  doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true, body).map {
                    _.prependValue(v)
                  }
                case _ => None
              }
            case _ => None
          }
        case EndpointInput.Query(name, m, _, _) +: inputsTail =>
          m.fromOptionalString(ctx.request.uri.query().get(name)) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
                _.prependValue(v)
              }
            case _ => None
          }
        case EndpointIO.Header(name, m, _, _) +: inputsTail =>
          m.fromOptionalString(ctx.request.headers.find(_.is(name.toLowerCase)).map(_.value())) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
                _.prependValue(v)
              }
            case _ => None
          }
        case EndpointIO.Body(m, _, _) +: inputsTail =>
          m.fromOptionalString(Some(body)) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, body).map {
                _.prependValue(v)
              }
            case _ => None
          }
        case EndpointInput.Mapped(wrapped, f, _, _) +: inputsTail =>
          doMatch(wrapped.asVectorOfSingle, ctx, canRemoveSlash, body).flatMap { result =>
            doMatch(inputsTail, result.ctx, result.canRemoveSlash, body).map {
              _.prependValue(f.asInstanceOf[Any => Any].apply(SeqToParams(result.values)))
            }
          }
        // TODO
//        case EndpointIO.Mapped(wrapped, f, _, _) +: inputsTail =>
//          doMatch(wrapped.asVectorOfSingle, ctx, canRemoveSlash, body).flatMap { result =>
//            doMatch(inputsTail, result.ctx, result.canRemoveSlash, body).map {
//              _.prependValue(f.asInstanceOf[Any => Any].apply(SeqToParams(result.values)))
//            }
//          }
      }
    }

    val inputDirectives: Directive1[I] = {
      val bodyDirective: Directive1[String] = if (hasBody(e.input)) entity(as[String]) else provide(null)
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

  private def hasBody[I](in: EndpointInput[I]): Boolean = {
    in match {
      case _: EndpointIO.Body[_, _]               => true
      case EndpointInput.Multiple(inputs)         => inputs.exists(hasBody(_))
      case EndpointInput.Mapped(wrapped, _, _, _) => hasBody(wrapped)
      case _                                      => false
    }
  }
}
