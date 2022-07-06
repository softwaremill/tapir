package sttp.tapir.server

class ServerRoutes[
    F[_],
    ROUTES
] private[tapir] (
    val endpoints: List[ServerEndpoint[_, F]],
    val routes: ROUTES
) {

  def map[ROUTES2](f: ROUTES => ROUTES2): ServerRoutes[F, ROUTES2] =
    new ServerRoutes(
      endpoints,
      f(routes)
    )

  def combine(that: ServerRoutes[F, ROUTES])(combineRoutes: (ROUTES, ROUTES) => ROUTES): ServerRoutes[F, ROUTES] =
    ServerRoutes.combine(this, that)(combineRoutes)
}
object ServerRoutes {

  def combine[F[_], ROUTES](a: ServerRoutes[F, ROUTES], b: ServerRoutes[F, ROUTES])(
      combineRoutes: (ROUTES, ROUTES) => ROUTES
  ): ServerRoutes[F, ROUTES] =
    new ServerRoutes[F, ROUTES](
      a.endpoints ++ b.endpoints,
      combineRoutes(a.routes, b.routes)
    )

  def one[F[_], ROUTES, R](serverEndpoint: ServerEndpoint[R, F])(interpret: ServerEndpoint[R, F] => ROUTES): ServerRoutes[F, ROUTES] =
    new ServerRoutes(
      List(serverEndpoint),
      interpret(serverEndpoint)
    )

  def fromList[F[_], ROUTES, R](
      serverEndpoint: List[ServerEndpoint[R, F]]
  )(interpret: List[ServerEndpoint[R, F]] => ROUTES): ServerRoutes[F, ROUTES] =
    new ServerRoutes(
      serverEndpoint,
      interpret(serverEndpoint)
    )
}
