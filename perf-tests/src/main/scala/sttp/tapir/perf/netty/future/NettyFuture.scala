package sttp.tapir.perf.netty.future

import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future
import sttp.tapir.perf.apis._
import sttp.monad.MonadError
import sttp.monad.FutureMonad
import scala.concurrent.ExecutionContext
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import ExecutionContext.Implicits.global
import cats.effect.IO

object Tapir extends Endpoints {

  implicit val mErr: MonadError[Future] = new FutureMonad()(ExecutionContext.Implicits.global)

  val serverEndpointGens = replyingWithDummyStr[Future](allEndpoints)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList
}

object NettyFuture {

  def runServer(endpoints: List[ServerEndpoint[Any, Future]]): IO[ServerRunner.KillSwitch] = {
    val declaredPort = 8080
    val declaredHost = "0.0.0.0"
    // Starting netty server
    val serverBinding: IO[NettyFutureServerBinding] =
      IO.fromFuture(IO(NettyFutureServer()
        .port(declaredPort)
        .host(declaredHost)
        .addEndpoints(endpoints)
        .start()))

    serverBinding.map(b => IO.fromFuture(IO(b.stop())))
  }
}

object TapirServer extends ServerRunner { override def start = NettyFuture.runServer(Tapir.genEndpoints(1)) }
object TapirMultiServer extends ServerRunner { override def start = NettyFuture.runServer(Tapir.genEndpoints(128)) }
