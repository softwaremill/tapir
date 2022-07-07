package sttp.tapir.server.netty.cats

import sttp.monad.MonadError
import sttp.tapir.integ.cats.ServerRoutesInstances
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}
import sttp.tapir.server.netty.Route

object NettyCatsServerRoutes extends ServerRoutesInstances {

  def combine[F[_]: MonadError](a: NettyServerRoutes[F], b: NettyServerRoutes[F]): NettyServerRoutes[F] =
    a.combine(b)((ra, rb) => Route.combine[F](List(ra, rb)))

  def combine[F[_]: MonadError](routes: Iterable[NettyServerRoutes[F]]): NettyServerRoutes[F] =
    routes.reduce(combine[F])

  // syntax
  implicit class NettyCatsServerEndpointsRoutesOps[F[_]: MonadError](thisRoutes: NettyServerRoutes[F]) {
    def combine(thatRoutes: NettyServerRoutes[F]): NettyServerRoutes[F] =
      NettyCatsServerRoutes.combine[F](thisRoutes, thatRoutes)
  }

  implicit class NettyCatsServerEndpointsRoutesIterableOps[F[_]: MonadError](theseRoutes: Iterable[NettyServerRoutes[F]]) {
    def combine(thoseRoutes: Iterable[NettyServerRoutes[F]]): NettyServerRoutes[F] =
      NettyCatsServerRoutes.combine[F](theseRoutes ++ thoseRoutes)
  }

  implicit class NettyCatsServerEndpointOps[F[_]](se: ServerEndpoint[Any, F]) {
    def toRoute(interpreter: NettyCatsServerInterpreter[F]): NettyServerRoutes[F] =
      ServerRoutes.one[F, Route[F], Any](se)(se => interpreter.toRoute(List(se)))
  }

  implicit class NettyCatsServerEndpointListOps[F[_]](se: List[ServerEndpoint[Any, F]]) {
    def toRoute(interpreter: NettyCatsServerInterpreter[F]): NettyServerRoutes[F] =
      ServerRoutes.fromList(se)(interpreter.toRoute)
  }
}
