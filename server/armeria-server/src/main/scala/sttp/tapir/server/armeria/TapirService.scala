package sttp.tapir.server.armeria

import com.linecorp.armeria.server.{HttpServiceWithRoutes, Route}
import java.util.{Set => JSet}
import scala.collection.JavaConverters._
import sttp.capabilities.Streams
import sttp.tapir.server.ServerEndpoint

trait TapirService[S <: Streams[S], F[_]] extends HttpServiceWithRoutes {
  def serverEndpoints: List[ServerEndpoint[S, F]]

  def armeriaServerOptions: ArmeriaServerOptions[F]

  // TODO(ikhoon): Use upstream's ExchangeType to optimize performance for non-streaming requests
  //               if https://github.com/line/armeria/pull/3956 is merged.
  private val routesMap: Map[Route, ExchangeType.Value] = serverEndpoints.flatMap(se => RouteMapping.toRoute(se.endpoint)).toMap
  override final val routes: JSet[Route] = routesMap.keySet.asJava
}
