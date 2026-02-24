package sttp.tapir.perf.nima

import cats.effect.{IO, Resource}
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

  def runServer(endpoints: List[ServerEndpoint[Any, Identity]], withServerLog: Boolean = false): Resource[IO, Unit] = {
    val declaredPort = Port
    val serverOptions = buildOptions(NimaServerOptions.customiseInterceptors, withServerLog)
    // Starting Nima server

    val handler = NimaServerInterpreter(serverOptions).toHandler(endpoints)
    val startServer = IO {
      WebServer
        .builder()
        .routing { builder =>
          builder.any(handler)
          ()
        }
        .port(declaredPort)
        .build()
        .start()
    }
    Resource.make(startServer)(server => IO(server.stop()).void).map(_ => ())
  }
}

object TapirServer extends ServerRunner { override def runServer = Nima.runServer(Tapir.genEndpointsNId(1)) }
object TapirMultiServer extends ServerRunner { override def runServer = Nima.runServer(Tapir.genEndpointsNId(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def runServer = Nima.runServer(Tapir.genEndpointsNId(128), withServerLog = true)
}
