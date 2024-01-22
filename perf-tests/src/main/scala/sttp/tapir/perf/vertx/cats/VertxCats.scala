package sttp.tapir.perf.vertx.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.perf.vertx.VertxRunner
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter
import sttp.tapir.server.vertx.cats.VertxCatsServerOptions

object Tapir extends Endpoints {
  def route(dispatcher: Dispatcher[IO]): Int => Router => Route = { (nRoutes: Int) => router =>
    val serverOptions = VertxCatsServerOptions.customiseInterceptors[IO](dispatcher).serverLog(None).options
    val interpreter = VertxCatsServerInterpreter(serverOptions)
    genEndpointsIO(nRoutes).map(interpreter.route(_)(router)).last
  }
}

class VertxCatsRunner(numRoutes: Int) {

  def start: IO[ServerRunner.KillSwitch] =
    Dispatcher.parallel[IO].allocated.flatMap { case (dispatcher, releaseDispatcher) =>
      VertxRunner.runServer(Tapir.route(dispatcher)(numRoutes)).map(releaseVertx => releaseVertx >> releaseDispatcher)
    }
}

object TapirServer extends VertxCatsRunner(numRoutes = 1) with ServerRunner
object TapirMultiServer extends VertxCatsRunner(numRoutes = 1) with ServerRunner
