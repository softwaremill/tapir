package sapi.server.akkahttp

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpHeader, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import sapi._
import sapi.internal.SeqToParams
import sapi.typelevel.{ParamsAsFnArgs, ParamsToTuple}

import scala.concurrent.Future

object EndpointToAkkaServer {

  def toDirective[I, E, O, T](e: Endpoint[I, E, O])(implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(paramsToTuple.toTuple(values))
    }
  }

  def toRoute[I, E, O, FN[_]](e: Endpoint[I, E, O])(logic: FN[Future[Either[E, O]]])(
      implicit paramsAsFnArgs: ParamsAsFnArgs[I, FN]): Route = {
    toDirective1(e) { values =>
      onSuccess(paramsAsFnArgs.applyFn(logic, values)) {
        case Left(v)  => outputToRoute(e.errorOutput, v)
        case Right(v) => outputToRoute(e.output, v)
      }
    }
  }

  private def outputToRoute[O](output: EndpointOutput.Multiple[O], v: O): Route = {
    val withIndex = output.outputs.zipWithIndex
    def vAt(i: Int) = v match {
      case p: Product => p.productElement(i)
      case _          => v
    }

    val body = withIndex.collectFirst {
      case (EndpointIO.Body(m, _, _), i) => m.toOptionalString(vAt(i))
    }.flatten

    val headers = withIndex
      .flatMap {
        case (EndpointIO.Header(name, m, _, _), i) => m.toOptionalString(vAt(i)).map(HttpHeader.parse(name, _))
        case _                                     => None
      }
      .collect {
        case ParsingResult.Ok(h, _) => h
        // TODO error on parse error?
      }

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
      case Method.GET => get
      case _          => post
    }

    // TODO: when parsing a query parameter/header/body/path fragment fails, provide an option to return a nice
    // error to the user (instead of a 404).

    def doMatch(inputs: Vector[EndpointInput.Single[_]],
                ctx: RequestContext,
                canRemoveSlash: Boolean): Option[(List[Any], RequestContext)] = {
      inputs match {
        case Vector() => Some((Nil, ctx))
        case EndpointInput.PathSegment(ss) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash => doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false)
            case Uri.Path.Segment(`ss`, pathTail)           => doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true)
            case _                                          => None
          }
        case EndpointInput.PathCapture(_, m, _, _) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash => doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false)
            case Uri.Path.Segment(s, pathTail) =>
              m.fromString(s) match {
                case DecodeResult.Value(v) =>
                  doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true).map {
                    case (values, ctx2) => (v :: values, ctx2)
                  }
                case _ => None
              }
            case _ => None
          }
        case EndpointInput.Query(name, m, _, _) +: inputsTail =>
          m.fromOptionalString(ctx.request.uri.query().get(name)) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true).map {
                case (values, ctx2) => (v :: values, ctx2)
              }
            case _ => None
          }
        case EndpointIO.Header(name, m, _, _) +: inputsTail =>
          m.fromOptionalString(ctx.request.headers.find(_.is(name.toLowerCase)).map(_.value())) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true).map {
                case (values, ctx2) => (v :: values, ctx2)
              }
            case _ => None
          }
      }
    }

    val inputDirectives: Directive1[I] = extractRequestContext.flatMap { ctx =>
      doMatch(e.input.inputs, ctx, canRemoveSlash = true) match {
        case Some((values, ctx2)) => provide(SeqToParams(values).asInstanceOf[I]) & mapRequestContext(_ => ctx2)
        case None                 => reject
      }
    }

    methodDirective & inputDirectives
  }
}
