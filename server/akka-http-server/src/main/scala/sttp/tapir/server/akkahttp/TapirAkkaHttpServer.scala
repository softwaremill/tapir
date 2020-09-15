package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server._
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.typelevel.{ParamsToTuple, ReplaceFirstInTuple}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait TapirAkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O, AkkaStream]) {
    def toDirective(implicit serverOptions: AkkaHttpServerOptions): Directive[(I, Future[Either[E, O]] => Route)] =
      new EndpointToAkkaServer(serverOptions).toDirective(e)

//    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T], akkaHttpOptions: AkkaHttpServerOptions): Directive[T] =
//      new EndpointToAkkaServer(akkaHttpOptions).toDirective(e)

    def toRoute(logic: I => Future[Either[E, O]])(implicit serverOptions: AkkaHttpServerOptions): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogic(logic))

    def toRouteRecoverErrors(
        logic: I => Future[O]
    )(implicit serverOptions: AkkaHttpServerOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Route = {
      new EndpointToAkkaServer(serverOptions).toRouteRecoverErrors(e)(logic)
    }
  }

  implicit class RichAkkaHttpServerEndpoint[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, AkkaStream, Future]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T], akkaHttpOptions: AkkaHttpServerOptions): Directive[T] =
      new EndpointToAkkaServer(akkaHttpOptions).toDirective(serverEndpoint.endpoint)

    def toRoute(implicit serverOptions: AkkaHttpServerOptions): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoint)
  }

  implicit class RichAkkaHttpServerEndpoints(serverEndpoints: List[ServerEndpoint[_, _, _, AkkaStream, Future]]) {
    def toRoute(implicit serverOptions: AkkaHttpServerOptions): Route = {
      new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoints)
    }
  }

  implicit class RichToFutureFunction[T, U](a: T => Future[U])(implicit ec: ExecutionContext) {
    def andThenFirst[U_TUPLE, T_TUPLE, O](
        l: U_TUPLE => Future[O]
    )(implicit replaceFirst: ReplaceFirstInTuple[T, U, T_TUPLE, U_TUPLE]): T_TUPLE => Future[O] = { tTuple =>
      val t = replaceFirst.first(tTuple)
      a(t).flatMap { u =>
        val uTuple = replaceFirst.replace(tTuple, u)
        l(uTuple)
      }
    }
  }

  implicit class RichToFutureOfEitherFunction[T, U, E](a: T => Future[Either[E, U]])(implicit ec: ExecutionContext) {
    def andThenFirstE[U_TUPLE, T_TUPLE, O](
        l: U_TUPLE => Future[Either[E, O]]
    )(implicit replaceFirst: ReplaceFirstInTuple[T, U, T_TUPLE, U_TUPLE]): T_TUPLE => Future[Either[E, O]] = { tTuple =>
      val t = replaceFirst.first(tTuple)
      a(t).flatMap {
        case Left(e) => Future.successful(Left(e))
        case Right(u) =>
          val uTuple = replaceFirst.replace(tTuple, u)
          l(uTuple)
      }
    }
  }
}
