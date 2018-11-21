package tapir.server.akkahttp

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpHeader, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import akka.http.scaladsl.server.{Directive, Directive1, Route}
import tapir._
import tapir.internal.SeqToParams
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

  // don't look below. The code is ugly.

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

    var bodyIndex: Option[Int] = None

    def doMatch(inputs: Vector[EndpointInput.Single[_]],
                ctx: RequestContext,
                canRemoveSlash: Boolean,
                nextValueIndex: Int): Option[(List[Any], RequestContext)] = {
      inputs match {
        case Vector() => Some((Nil, ctx))
        case EndpointInput.PathSegment(ss) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash =>
              doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false, nextValueIndex)
            case Uri.Path.Segment(`ss`, pathTail) =>
              doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true, nextValueIndex)
            case _ => None
          }
        case EndpointInput.PathCapture(m, _, _, _) +: inputsTail =>
          ctx.unmatchedPath match {
            case Uri.Path.Slash(pathTail) if canRemoveSlash =>
              doMatch(inputs, ctx.withUnmatchedPath(pathTail), canRemoveSlash = false, nextValueIndex)
            case Uri.Path.Segment(s, pathTail) =>
              m.fromString(s) match {
                case DecodeResult.Value(v) =>
                  doMatch(inputsTail, ctx.withUnmatchedPath(pathTail), canRemoveSlash = true, nextValueIndex + 1).map {
                    case (values, ctx2) => (v :: values, ctx2)
                  }
                case _ => None
              }
            case _ => None
          }
        case EndpointInput.Query(name, m, _, _) +: inputsTail =>
          m.fromOptionalString(ctx.request.uri.query().get(name)) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, nextValueIndex + 1).map {
                case (values, ctx2) => (v :: values, ctx2)
              }
            case _ => None
          }
        case EndpointIO.Header(name, m, _, _) +: inputsTail =>
          m.fromOptionalString(ctx.request.headers.find(_.is(name.toLowerCase)).map(_.value())) match {
            case DecodeResult.Value(v) =>
              doMatch(inputsTail, ctx, canRemoveSlash = true, nextValueIndex + 1).map {
                case (values, ctx2) => (v :: values, ctx2)
              }
            case _ => None
          }
        case EndpointIO.Body(m, _, _) +: inputsTail =>
          bodyIndex = Some(nextValueIndex)
          doMatch(inputsTail, ctx, canRemoveSlash = true, nextValueIndex + 1).map {
            case (values, ctx2) => (null :: values, ctx2)
          }
      }
    }

    val inputDirectives: Directive1[I] = extractRequestContext.flatMap { ctx =>
      doMatch(e.input.inputs, ctx, canRemoveSlash = true, 0) match {
        case Some((values, ctx2)) =>
          def provideValues(v: Seq[Any]) = provide(SeqToParams(v).asInstanceOf[I]) & mapRequestContext(_ => ctx2)

          bodyIndex match {
            case None => provideValues(values)
            case Some(i) =>
              entity(as[String]).flatMap { body =>
                provideValues(values.updated(i, body))
              }
          }
        case None => reject
      }
    }

    methodDirective & inputDirectives
  }
}
