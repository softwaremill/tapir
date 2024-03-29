package sttp.tapir.server.finatra

import com.twitter.finagle.http.Method._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.Controller
import com.twitter.util.Future

trait TapirController { self: Controller =>
  def addTapirRoute(route: FinatraRoute): Unit = {
    route.method match {
      case Get =>
        get(route.path)(route.handler)
      case Post =>
        post(route.path)(route.handler)
      case Put =>
        put(route.path)(route.handler)
      case Head =>
        head(route.path)(route.handler)
      case Patch =>
        patch(route.path)(route.handler)
      case Delete =>
        delete(route.path)(route.handler)
      case Trace =>
        trace[Request, Future[Response]](route.path)(route.handler)
      case Options =>
        options(route.path)(route.handler)
      case _ =>
        get(route.path)(route.handler)
        post(route.path)(route.handler)
        put(route.path)(route.handler)
        head(route.path)(route.handler)
        patch(route.path)(route.handler)
        delete(route.path)(route.handler)
        options(route.path)(route.handler)
    }
  }
}
