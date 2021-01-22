package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.mvc._
import play.api.routing.Router.Routes
import sttp.capabilities.Effect
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait TapirPlayServer {
  implicit class RichPlayEndpoint[I, E, O](e: Endpoint[I, E, O, Effect[Future]]) {
    @deprecated("Use PlayServerInterpreter.toRoute", since = "0.17.1")
    def toRoute(
        logic: I => Future[Either[E, O]]
    )(implicit ec: ExecutionContext, mat: Materializer, serverOptions: PlayServerOptions): Routes = PlayServerInterpreter.toRoute(e)(logic)

    def toRouteRecoverErrors(logic: I => Future[O])(implicit
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E],
        ec: ExecutionContext,
        mat: Materializer,
        serverOptions: PlayServerOptions
    ): Routes = PlayServerInterpreter.toRouteRecoverErrors(e)(logic)
  }

  implicit class RichPlayServerEndpoint[I, E, O](e: ServerEndpoint[I, E, O, Effect[Future], Future]) {
    @deprecated("Use PlayServerInterpreter.toRoute", since = "0.17.1")
    def toRoute(implicit ec: ExecutionContext, mat: Materializer, serverOptions: PlayServerOptions): Routes =
      PlayServerInterpreter.toRoute(e)
  }

  implicit class RichPlayServerEndpoints[I, E, O](serverEndpoints: List[ServerEndpoint[_, _, _, Effect[Future], Future]]) {
    @deprecated("Use PlayServerInterpreter.toRoute", since = "0.17.1")
    def toRoute(implicit ec: ExecutionContext, mat: Materializer, serverOptions: PlayServerOptions): Routes =
      PlayServerInterpreter.toRoute(serverEndpoints)
  }

  implicit def actionBuilderFromPlayServerOptions(implicit playServerOptions: PlayServerOptions): ActionBuilder[Request, AnyContent] =
    playServerOptions.defaultActionBuilder
}
