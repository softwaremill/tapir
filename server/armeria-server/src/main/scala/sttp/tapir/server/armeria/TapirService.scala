package sttp.tapir.server.armeria

import com.linecorp.armeria.common.ExchangeType
import com.linecorp.armeria.server.{HttpServiceWithRoutes, Route, RoutingContext}
import java.util.{Set => JSet}
import scala.collection.JavaConverters._
import sttp.capabilities.Streams
import sttp.tapir.server.ServerEndpoint

trait TapirService[S <: Streams[S], F[_]] extends HttpServiceWithRoutes {

  def serverEndpoints: List[ServerEndpoint[S, F]]

  def armeriaServerOptions: ArmeriaServerOptions[F]

  // TODO(ikhoon): Use upstream's ExchangeType to optimize performance for non-streaming requests
  //               if https://github.com/line/armeria/pull/3956 is merged.
  private val routesMap: Map[Route, ExchangeType] = serverEndpoints.flatMap(se => RouteMapping.toRoute(se.endpoint)).toMap
  override final val routes: JSet[Route] = routesMap.keySet.asJava

  override def exchangeType(routingContext: RoutingContext): ExchangeType = {
    if (routingContext.hasResult()) {
      routesMap
        .get(routingContext.result().route())
        .getOrElse(ExchangeType.BIDI_STREAMING)
    } else {
      ExchangeType.BIDI_STREAMING
    }
  }
}
