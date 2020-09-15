package sttp.tapir.server.http4s

import org.http4s.HttpRoutes
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.ztapir._
import zio.interop.catz._
import zio.{Task, URIO, ZIO}

package object ztapir {
  implicit class RichZEndpointRoutes[I, E, O](e: ZEndpoint[I, E, O]) {
    def toRoutes(logic: I => ZIO[Any, E, O])(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] =
      e.zServerLogic(logic).toRoutes

    def toRoutesR[R](logic: I => ZIO[R, E, O])(implicit serverOptions: Http4sServerOptions[Task]): URIO[R, HttpRoutes[Task]] =
      e.zServerLogic(logic).toRoutesR
  }

  implicit class RichZServerEndpointRoutes[I, E, O](se: ZServerEndpoint[Any, I, E, O]) {
    def toRoutes(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] = List(se).toRoutes
  }

  implicit class RichZServerEndpointsRoutes[I, E, O](serverEndpoints: List[ZServerEndpoint[Any, _, _, _]]) {
    def toRoutes(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(serverEndpoints)
    }
  }

  implicit class RichZServerEndpointRRoutes[R, I, E, O](se: ZServerEndpoint[R, I, E, O]) {
    def toRoutesR(implicit serverOptions: Http4sServerOptions[Task]): URIO[R, HttpRoutes[Task]] = List(se).toRoutesR
  }

  implicit class RichZServerEndpointsRRoutes[R](serverEndpoints: List[ZServerEndpoint[R, _, _, _]]) {
    def toRoutesR(implicit serverOptions: Http4sServerOptions[Task]): URIO[R, HttpRoutes[Task]] =
      URIO.access[R] { env =>
        val taskServerEndpoints = serverEndpoints.map(toTaskEndpointR(env, _))
        new EndpointToHttp4sServer(serverOptions).toRoutes(taskServerEndpoints)
      }
  }

  private def toTaskEndpointR[R, I, E, O](env: R, se: ZServerEndpoint[R, I, E, O]): ServerEndpoint[I, E, O, Any, Task] = {
    ServerEndpoint(
      se.endpoint,
      _ => (i: I) => se.logic(new ZIOMonadError[R])(i).provide(env)
    )
  }
}
