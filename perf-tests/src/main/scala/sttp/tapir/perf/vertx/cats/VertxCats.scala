package sttp.tapir.perf.vertx.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter
import sttp.tapir.perf.vertx.VertxRunner

object Tapir extends Endpoints {
  val serverEndpointGens = replyingWithDummyStr(allEndpoints, IO.pure)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList

  def route(dispatcher: Dispatcher[IO]): Int => Router => Route = { (nRoutes: Int) => router =>
    val interpreter = VertxCatsServerInterpreter(dispatcher)
    genEndpoints(nRoutes).map(interpreter.route(_)(router)).last
  }
}

class VertxCatsRunner(numRoutes: Int) extends ServerRunner {

  override def start =
    Dispatcher.parallel[IO].allocated.flatMap { case (dispatcher, releaseDispatcher) =>
      VertxRunner.runServer(Tapir.route(dispatcher)(numRoutes)).map(_.flatMap(_ => releaseDispatcher))
    }
}

object TapirServer extends VertxCatsRunner(numRoutes = 1)
object TapirMultiServer extends VertxCatsRunner(numRoutes = 1)
