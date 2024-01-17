package sttp.tapir.perf.netty.cats

import cats.effect.IO
import cats.effect.kernel.Resource
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.cats.NettyCatsServer

object Tapir extends Endpoints {

  val serverEndpointGens = replyingWithDummyStr(allEndpoints, IO.pure)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList
}

object NettyCats {

  def runServer(endpoints: List[ServerEndpoint[Any, IO]]): IO[ServerRunner.KillSwitch] = {
    val declaredPort = Port
    val declaredHost = "0.0.0.0"
    // Starting netty server
    NettyCatsServer
      .io()
      .flatMap { server =>
        Resource.make(
          server
            .port(declaredPort)
            .host(declaredHost)
            .addEndpoints(endpoints)
            .start()
        )(binding => binding.stop())
      }
      .allocated
      .map(_._2)
  }
}

object TapirServer extends ServerRunner { override def start = NettyCats.runServer(Tapir.genEndpoints(1)) }
object TapirMultiServer extends ServerRunner { override def start = NettyCats.runServer(Tapir.genEndpoints(128)) }
