package sapi.server.akkahttp

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import sapi._
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.Tupler
import shapeless.{HList, HNil}

import scala.concurrent.Future

object EndpointToAkkaServer {

  def toDirective[T, I <: HList, O <: HList, OE <: HList](e: Endpoint[I, O, OE])(implicit t: Tupler.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(t(values.asInstanceOf[I]))
    }
  }

  def toRoute[T, I <: HList, O <: HList, OE <: HList, TO, TOE, F](e: Endpoint[I, O, OE])(logic: F)(
      implicit oToTuple: HListToResult.Aux[O, TO],
      oeToTuple: HListToResult.Aux[OE, TOE],
      tt: FnToProduct.Aux[F, I => Future[Either[TOE, TO]]]): Route = {
    toDirective1(e) { values =>
      onSuccess(tt(logic)(values.asInstanceOf[I])) {
        case Left(v)  => outputToRoute(e.errorOutput, v)
        case Right(v) => outputToRoute(e.output, v)
      }
    }
  }

  private def outputToRoute[O <: HList, TO](output: EndpointOutput.Multiple[O], v: TO)(
      implicit oToTuple: HListToResult.Aux[O, TO]): Route = {
    output.outputs.headOption match {
      case None                           => complete("")
      case Some(EndpointIO.Body(m, _, _)) => complete(m.toOptionalString(v))
    }
  }

  private def toDirective1(e: Endpoint[_, _, _]): Directive1[HList] = {

    import akka.http.scaladsl.server.Directives._
    import akka.http.scaladsl.server._

    val methodDirective = e.method match {
      case Method.GET => get
      case _          => post
    }

    // TODO: when parsing a query parameter/header/body/path fragment fails, provide an option to return a nice
    // error to the user (instead of a 404).

    def doMatch(inputs: Vector[EndpointInput.Single[_]], ctx: RequestContext, canRemoveSlash: Boolean): Option[(HList, RequestContext)] = {
      inputs match {
        case Vector() => Some((HNil, ctx))
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
      }
    }

    val inputDirectives: Directive1[HList] = extractRequestContext.flatMap { ctx =>
      doMatch(e.input.inputs, ctx, canRemoveSlash = true) match {
        case Some((values, ctx2)) => provide(values: HList) & mapRequestContext(_ => ctx2)
        case None                 => reject
      }
    }

    methodDirective & inputDirectives
  }
}
