package sapi.server.akkahttp

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import sapi.{Id, _}
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.Tupler
import shapeless.{HList, HNil}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object EndpointToAkkaServer {

  /*
  val params = values.foldRight(HNil: HList) {
        case (el, hlist) =>
          el :: hlist
      }
   */

  def toDirective[T, I <: HList](e: Endpoint[Id, I, _])(implicit t: Tupler.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(t(values.asInstanceOf[I]))
    }
  }

  def toRoute[T, I <: HList, O, F](e: Endpoint[Id, I, O])(logic: F)(implicit tt: FnToProduct.Aux[F, I => Future[O]]): Route = {
    toDirective1(e) { values =>
      onSuccess(tt(logic)(values.asInstanceOf[I])) { x =>
        complete(e.output.toString(x))
      }
    }
  }

  private def toDirective1(e: Endpoint[Id, _, _]): Directive1[HList] = {

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
        case EndpointInput.PathCapture(m) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash => doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false)
            case Uri.Path.Segment(s, pathTail) =>
              m.fromString(s) match {
                case Success(v) =>
                  doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true).map {
                    case (values, ctx2) => (v :: values, ctx2)
                  }
                case Failure(_) => None
              }
            case _ => None
          }
        case EndpointInput.Query(name, m) +: inputsTail =>
          ctx.request.uri.query().get(name).flatMap((m.fromString _).andThen(_.toOption)).flatMap { value =>
            doMatch(inputsTail, ctx, canRemoveSlash = true).map {
              case (values, ctx2) => (value :: values, ctx2)
            }
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
