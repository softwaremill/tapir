package sttp.tapir.perf.netty.cats

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import fs2.Stream
import sttp.tapir.{CodecFormat, webSocketBody}
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.cats.NettyCatsServer
import sttp.tapir.server.netty.cats.NettyCatsServerOptions
import sttp.ws.WebSocketFrame
import sttp.capabilities.fs2.Fs2Streams

import scala.concurrent.duration._

object Tapir extends Endpoints {
  val wsResponseStream = Stream.fixedRate[IO](WebSocketSingleResponseLag, dampen = false)
  val wsEndpoint = wsBaseEndpoint
    .out(
      webSocketBody[Long, CodecFormat.TextPlain, Long, CodecFormat.TextPlain](Fs2Streams[IO])
        .concatenateFragmentedFrames(false)
        .autoPongOnPing(false)
        .ignorePong(true)
        .autoPing(None)
    )
}

object NettyCats {
  val wsServerEndpoint = Tapir.wsEndpoint.serverLogicSuccess(_ =>
    IO.pure { (in: Stream[IO, Long]) =>
      Tapir.wsResponseStream.evalMap(_ => IO.realTime.map(_.toMillis)).concurrently(in.as(()))
    }
  )
  def runServer(endpoints: List[ServerEndpoint[Any, IO]], withServerLog: Boolean = false): IO[ServerRunner.KillSwitch] = {
    val declaredPort = Port
    val declaredHost = "0.0.0.0"
    (for {
      dispatcher <- Dispatcher.parallel[IO]
      serverOptions = buildOptions(NettyCatsServerOptions.customiseInterceptors(dispatcher), withServerLog)
      server <- NettyCatsServer.io()
      _ <-
        Resource.make(
          server
            .port(declaredPort)
            .host(declaredHost)
            .addEndpoints(wsServerEndpoint :: endpoints)
            .start()
        )(binding => binding.stop())
    } yield ()).allocated.map(_._2)
  }
}

object TapirServer extends ServerRunner { override def start = NettyCats.runServer(Tapir.genEndpointsIO(1)) }
object TapirMultiServer extends ServerRunner { override def start = NettyCats.runServer(Tapir.genEndpointsIO(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def start = NettyCats.runServer(Tapir.genEndpointsIO(128), withServerLog = true)
}
