package tapir.example

import org.http4s.{EntityBody, HttpRoutes}
import _root_.zio.{IO, Task}
import tapir.Endpoint
import tapir.server.http4s.Http4sServerOptions
import _root_.zio.interop.catz._
import tapir.server.ServerEndpoint

package object zio {
  implicit class ZioEndpoint[I, E, O](e: Endpoint[I, E, O, EntityBody[Task]]) {
    def toZioRoutes(logic: I => IO[E, O])(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] = {
      import tapir.server.http4s._
      e.toRoutes(i => logic(i).either)
    }

    def zioServerLogic(logic: I => IO[E, O]): ServerEndpoint[I, E, O, EntityBody[Task], Task] = ServerEndpoint(e, logic(_).either)
  }
}
