package sttp.tapir.perf.nima

import cats.effect.IO
import io.helidon.webserver.WebServer
import sttp.shared.Identity
import sttp.tapir.perf.apis._
import sttp.tapir.perf.Common._
import sttp.tapir.server.nima.{NimaServerInterpreter, NimaServerOptions}
import sttp.tapir.server.ServerEndpoint

object Tapir extends Endpoints {
  def genEndpointsNId(count: Int): List[ServerEndpoint[Any, Identity]] = genServerEndpoints[Identity](count)(x => x: Identity[String])
}

object Nima {

  def runServer(endpoints: List[ServerEndpoint[Any, Identity]], withServerLog: Boolean = false): IO[ServerRunner.KillSwitch] = {
    val declaredPort = Port
    val serverOptions = buildOptions(NimaServerOptions.customiseInterceptors, withServerLog)
    // Starting Nima server

    val handler = NimaServerInterpreter(serverOptions).toHandler(endpoints)
    val server = WebServer
      .builder()
      .routing { builder =>
        builder.any(handler)
        ()
      }
      .port(declaredPort)
      .build()
      .start()
    IO(IO { val _ = server.stop() })
  }
}

object TapirServer extends ServerRunner { override def start = Nima.runServer(Tapir.genEndpointsNId(1)) }
object TapirMultiServer extends ServerRunner { override def start = Nima.runServer(Tapir.genEndpointsNId(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def start = Nima.runServer(Tapir.genEndpointsNId(128), withServerLog = true)
}
