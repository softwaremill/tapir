package sttp.tapir.perf.vertx.cats

import cats.effect.{IO, Resource}
import cats.effect.std.Dispatcher
import fs2.Stream
import io.vertx.core.Vertx
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.perf.vertx.VertxRunner
import sttp.tapir.server.vertx.cats.{VertxCatsServerInterpreter, VertxCatsServerOptions}

object Tapir extends Endpoints {
  import sttp.tapir._
  // Websocket response is returned with a lag, so that we can have more concurrent users talking to the server.
  // This lag is not relevant for measurements, because the server returns a timestamp after having a response ready to send back,
  // so the client can measure only the latency of the server stack handling the response.
  val wsResponseStream = Stream.fixedRate[IO](WebSocketSingleResponseLag, dampen = false)

  def wsLaggedPipe: fs2.Pipe[IO, Long, Long] = { requestStream =>
    wsResponseStream.evalMap(_ => IO.realTime.map(_.toMillis)).concurrently(requestStream.as(()))
  }
  def route(dispatcher: Dispatcher[IO], withServerLog: Boolean): Int => Vertx => Router => Route = {
    (nRoutes: Int) => _ => (router: Router) =>
      val serverOptions = buildOptions(VertxCatsServerOptions.customiseInterceptors[IO](dispatcher), withServerLog)
      val interpreter = VertxCatsServerInterpreter(serverOptions)
      val wsServerEndpoint = wsBaseEndpoint
        .out(
          webSocketBody[Long, CodecFormat.TextPlain, Long, CodecFormat.TextPlain](Fs2Streams[IO])
            .concatenateFragmentedFrames(false)
        )
        .serverLogicSuccess(_ => IO.pure(wsLaggedPipe))
      (wsServerEndpoint :: genEndpointsIO(nRoutes)).map(interpreter.route(_)(router)).last
  }
}

class VertxCatsRunner(numRoutes: Int, withServerLog: Boolean = false) {

  def runServer: Resource[IO, Unit] = Dispatcher.parallel[IO].flatMap { dispatcher =>
    VertxRunner.runServer(Tapir.route(dispatcher, withServerLog)(numRoutes))
  }
}

object TapirServer extends VertxCatsRunner(numRoutes = 1) with ServerRunner
object TapirMultiServer extends VertxCatsRunner(numRoutes = 128) with ServerRunner
object TapirInterceptorMultiServer extends VertxCatsRunner(numRoutes = 128, withServerLog = true) with ServerRunner
