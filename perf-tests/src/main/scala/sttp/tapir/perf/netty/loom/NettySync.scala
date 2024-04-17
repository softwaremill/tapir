package sttp.tapir.perf.netty.loom

import cats.effect.IO
import sttp.tapir.perf.apis._
import sttp.tapir.perf.Common._
import sttp.tapir.server.netty.loom._
import sttp.tapir.server.ServerEndpoint

object Tapir extends Endpoints

object NettySync {

  def runServer(endpoints: List[ServerEndpoint[Any, Id]], withServerLog: Boolean = false): IO[ServerRunner.KillSwitch] = {
    val declaredPort = Port
    val declaredHost = "0.0.0.0"
    val serverOptions = buildOptions(NettySyncServerOptions.customiseInterceptors, withServerLog)
    // Starting netty server
    val serverBinding: NettySyncServerBinding =
      NettySyncServer(serverOptions)
        .port(declaredPort)
        .host(declaredHost)
        .addEndpoints(endpoints)
        .start()
    IO(IO(serverBinding.stop()))
  }
}

object TapirServer extends ServerRunner { override def start = NettySync.runServer(Tapir.genEndpointsId(1)) }
object TapirMultiServer extends ServerRunner { override def start = NettySync.runServer(Tapir.genEndpointsId(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def start = NettySync.runServer(Tapir.genEndpointsId(128), withServerLog = true)
}
