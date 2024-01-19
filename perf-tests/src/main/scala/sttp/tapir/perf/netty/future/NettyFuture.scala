package sttp.tapir.perf.netty.future

import cats.effect.IO
import sttp.tapir.perf.apis._
import sttp.tapir.perf.Common._
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import scala.concurrent.Future

object Tapir extends Endpoints 

object NettyFuture {

  def runServer(endpoints: List[ServerEndpoint[Any, Future]]): IO[ServerRunner.KillSwitch] = {
    val declaredPort = Port
    val declaredHost = "0.0.0.0"
    // Starting netty server
    val serverBinding: IO[NettyFutureServerBinding] =
      IO.fromFuture(
        IO(
          NettyFutureServer()
            .port(declaredPort)
            .host(declaredHost)
            .addEndpoints(endpoints)
            .start()
        )
      )

    serverBinding.map(b => IO.fromFuture(IO(b.stop())))
  }
}

object TapirServer extends ServerRunner { override def start = NettyFuture.runServer(Tapir.genEndpointsFuture(1)) }
object TapirMultiServer extends ServerRunner { override def start = NettyFuture.runServer(Tapir.genEndpointsFuture(128)) }
