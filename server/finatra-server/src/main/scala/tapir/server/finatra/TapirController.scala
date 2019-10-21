package tapir.server.finatra
import com.twitter.finatra.http.Controller

trait TapirController { self: Controller =>
  def addTapirRoute(route: FinatraRoute): Unit = {
    any(route.path)(route.handler)
    any(route.path + "/")(route.handler)
  }
}
