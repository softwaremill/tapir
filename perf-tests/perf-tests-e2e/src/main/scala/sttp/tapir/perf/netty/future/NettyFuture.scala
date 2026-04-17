package sttp.tapir.perf.netty.future

import cats.effect.{IO, Resource}
import sttp.tapir.perf.apis._
import sttp.tapir.perf.Common._
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding, NettyFutureServerOptions}
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import scala.concurrent.Future

object Tapir extends Endpoints

object NettyFuture {

  def runServer(endpoints: List[ServerEndpoint[Any, Future]], withServerLog: Boolean = false): Resource[IO, Unit] = {
    val declaredPort = Port
    val declaredHost = "0.0.0.0"
    val serverOptions = buildOptions(NettyFutureServerOptions.customiseInterceptors, withServerLog)
    // Starting netty server
    val serverBinding: IO[NettyFutureServerBinding] =
      IO.fromFuture(
        IO(
          NettyFutureServer(serverOptions)
            .port(declaredPort)
            .host(declaredHost)
            .addEndpoints(endpoints)
            .start()
        )
      )
    Resource.make(serverBinding)(b => IO.fromFuture(IO(b.stop()))).map(_ => ())
  }
}

object TapirServer extends ServerRunner { override def runServer = NettyFuture.runServer(Tapir.genEndpointsFuture(1)) }
object TapirMultiServer extends ServerRunner { override def runServer = NettyFuture.runServer(Tapir.genEndpointsFuture(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def runServer = NettyFuture.runServer(Tapir.genEndpointsFuture(128), withServerLog = true)
}
