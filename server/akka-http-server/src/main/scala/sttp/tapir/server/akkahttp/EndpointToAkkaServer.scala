package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.{MediaType => _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import com.github.ghik.silencer.silent
import sttp.tapir._
import sttp.tapir.server.{ServerDefaults, ServerEndpoint}
import sttp.tapir.typelevel.ParamsToTuple

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

class EndpointToAkkaServer(serverOptions: AkkaHttpServerOptions) {
  def toDirective[I, E, O, T](e: Endpoint[I, E, O, AkkaStream])(implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(paramsToTuple.toTuple(values))
    }
  }

  @silent("never used")
  def toRouteRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, AkkaStream]
  )(logic: I => Future[O])(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Route = {
    def reifyFailedFuture(f: Future[O]): Future[Either[E, O]] = {
      import ExecutionContext.Implicits.global
      f.map(Right(_): Either[E, O]).recover {
        case e: Throwable if implicitly[ClassTag[E]].runtimeClass.isInstance(e) => Left(e.asInstanceOf[E]): Either[E, O]
      }
    }

    toRoute(e.serverLogic(logic.andThen(reifyFailedFuture)))
  }

  def toRoute[I, E, O](se: ServerEndpoint[I, E, O, AkkaStream, Future]): Route = {
    toDirective1(se.endpoint) { values =>
      extractLog { log =>
        mapResponse(resp => { serverOptions.logRequestHandling.requestHandled(se.endpoint, resp.status.intValue())(log); resp }) {
          onComplete(se.logic(values)) {
            case Success(Left(v))  => OutputToAkkaRoute(ServerDefaults.StatusCodes.error.code, se.endpoint.errorOutput, v)
            case Success(Right(v)) => OutputToAkkaRoute(ServerDefaults.StatusCodes.success.code, se.endpoint.output, v)
            case Failure(e) =>
              serverOptions.logRequestHandling.logicException(se.endpoint, e)(log)
              throw e
          }
        }
      }
    }
  }

  def toRoute(serverEndpoints: List[ServerEndpoint[_, _, _, AkkaStream, Future]]): Route = {
    serverEndpoints.map(se => toRoute(se)).foldLeft(RouteDirectives.reject: Route)(_ ~ _)
  }

  private def toDirective1[I, E, O](e: Endpoint[I, E, O, AkkaStream]): Directive1[I] = new EndpointToAkkaDirective(serverOptions)(e)
}
