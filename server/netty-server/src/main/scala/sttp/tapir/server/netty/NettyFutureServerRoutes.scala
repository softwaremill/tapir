package sttp.tapir.server.netty

import sttp.monad.FutureMonad
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

import scala.concurrent.{ExecutionContext, Future}

object NettyFutureServerRoutes {

  def combine(a: FutureServerRoutes, b: FutureServerRoutes)(implicit ec: ExecutionContext): FutureServerRoutes = {
    implicit val monad: FutureMonad = new FutureMonad()
    a.combine(b)((ra, rb) => Route.combine(List(ra, rb)))
  }

  def combine(routes: Iterable[FutureServerRoutes])(implicit ec: ExecutionContext): FutureServerRoutes =
    routes.reduce(combine)

  // syntax
  implicit class NettyFutureServerEndpointsRoutesOps(thisRoutes: FutureServerRoutes) {
    def combine(thatRoutes: FutureServerRoutes)(implicit ec: ExecutionContext): FutureServerRoutes =
      NettyFutureServerRoutes.combine(thisRoutes, thatRoutes)
  }

  implicit class NettyFutureServerEndpointsRoutesIterableOps(theseRoutes: Iterable[FutureServerRoutes]) {
    def combine(thoseRoutes: Iterable[FutureServerRoutes])(implicit ec: ExecutionContext): FutureServerRoutes =
      NettyFutureServerRoutes.combine(theseRoutes ++ thoseRoutes)
  }

  implicit class NettyFutureServerEndpointOps(se: ServerEndpoint[Any, Future]) {
    def toRoute(interpreter: NettyFutureServerInterpreter)(implicit ec: ExecutionContext): FutureServerRoutes =
      ServerRoutes.one(se)(se => interpreter.toRoute(List(se)))
  }

  implicit class NettyFutureServerEndpointListOps(se: List[ServerEndpoint[Any, Future]]) {
    def toRoute(interpreter: NettyFutureServerInterpreter)(implicit ec: ExecutionContext): FutureServerRoutes =
      ServerRoutes.fromList(se)(interpreter.toRoute)
  }
}
