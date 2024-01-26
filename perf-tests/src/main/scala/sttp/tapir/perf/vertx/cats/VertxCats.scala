package sttp.tapir.perf.vertx.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.perf.vertx.VertxRunner
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter
import sttp.tapir.server.vertx.cats.VertxCatsServerOptions

object Tapir extends Endpoints {
  def route(dispatcher: Dispatcher[IO], withServerLog: Boolean): Int => Router => Route = { (nRoutes: Int) => (router: Router) =>
    val serverOptions = buildOptions(VertxCatsServerOptions.customiseInterceptors[IO](dispatcher), withServerLog)
    val interpreter = VertxCatsServerInterpreter(serverOptions)
    genEndpointsIO(nRoutes).map(interpreter.route(_)(router)).last
  }
}

class VertxCatsRunner(numRoutes: Int, withServerLog: Boolean = false) {

  def start: IO[ServerRunner.KillSwitch] =
    Dispatcher.parallel[IO].allocated.flatMap { case (dispatcher, releaseDispatcher) =>
      VertxRunner.runServer(Tapir.route(dispatcher, withServerLog)(numRoutes)).map(releaseVertx => releaseVertx >> releaseDispatcher)
    }
}

object TapirServer extends VertxCatsRunner(numRoutes = 1) with ServerRunner
object TapirMultiServer extends VertxCatsRunner(numRoutes = 128) with ServerRunner
object TapirInterceptorMultiServer extends VertxCatsRunner(numRoutes = 128, withServerLog = true) with ServerRunner
