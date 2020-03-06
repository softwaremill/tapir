package sttp.tapir.example

import org.http4s.{EntityBody, HttpRoutes}
import _root_.zio.{ZIO, URIO, IO, Task}
import sttp.tapir.Endpoint
import sttp.tapir.server.http4s.Http4sServerOptions
import _root_.zio.interop.catz._
import sttp.tapir.server.ServerEndpoint

package object zio {
  implicit class ZioEndpoint[I, E, O](e: Endpoint[I, E, O, EntityBody[Task]]) {
    def toZioRoutes(logic: I => IO[E, O])(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] = {
      import sttp.tapir.server.http4s._
      e.toRoutes(i => logic(i).either)
    }

    def toZioRoutesR[R](logic: I => ZIO[R, E, O])(implicit serverOptions: Http4sServerOptions[Task]): URIO[R, HttpRoutes[Task]] = {
      import sttp.tapir.server.http4s._
      URIO.access[R](env => e.toRoutes(i => logic(i).provide(env).either))
    }

    def zioServerLogic(logic: I => IO[E, O]): ServerEndpoint[I, E, O, EntityBody[Task], Task] = ServerEndpoint(e, logic(_).either)
  }
}
