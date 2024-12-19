package sttp.tapir.server.netty.sync.perf

import ox.*
import ox.channels.*
import ox.flow.Flow
import sttp.shared.Identity
import sttp.tapir.server.netty.sync.NettySyncServerOptions
import sttp.tapir.server.netty.sync.NettySyncServerBinding
import sttp.tapir.server.netty.sync.NettySyncServer

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.EndpointExtensions.*
import sttp.tapir.server.netty.sync.OxStreams
import sttp.tapir.Endpoint
import sttp.capabilities.WebSockets
import scala.concurrent.duration._

object NettySyncServerRunner {
  val LargeInputSize = 5 * 1024L * 1024L
  val WebSocketSingleResponseLag = 100.millis

  type EndpointGen = Int => PublicEndpoint[_, String, String, Any]
  type ServerEndpointGen[F[_]] = Int => ServerEndpoint[Any, F]
  def serverEndpoints[F[_]](reply: String => F[String]): List[ServerEndpointGen[F]] = {
    List(
      { (n: Int) =>
        endpoint.get
          .in("path" + n.toString)
          .in(path[Int]("id"))
          .out(stringBody)
          .serverLogicSuccess { id =>
            reply((id + n).toString)
          }
      },
      { (n: Int) =>
        endpoint.post
          .in("path" + n.toString)
          .in(stringBody)
          .maxRequestBodyLength(LargeInputSize + 1024L)
          .out(stringBody)
          .serverLogicSuccess { (body: String) =>
            reply(s"Ok [$n], string length = ${body.length}")
          }
      },
      { (n: Int) =>
        endpoint.post
          .in("pathBytes" + n.toString)
          .in(byteArrayBody)
          .maxRequestBodyLength(LargeInputSize + 1024L)
          .out(stringBody)
          .serverLogicSuccess { (body: Array[Byte]) =>
            reply(s"Ok [$n], bytes length = ${body.length}")
          }
      }
    )
  }

  val wsBaseEndpoint = endpoint.get.in("ws" / "ts")

  val wsPipe: OxStreams.Pipe[Long, Long] = in =>
    in.drain()
      .merge(
        Flow.tick(WebSocketSingleResponseLag).map(_ => System.currentTimeMillis()),
        propagateDoneLeft = true
      )

  val wsEndpoint: Endpoint[Unit, Unit, Unit, OxStreams.Pipe[Long, Long], OxStreams with WebSockets] = wsBaseEndpoint
    .out(
      webSocketBody[Long, CodecFormat.TextPlain, Long, CodecFormat.TextPlain](OxStreams)
        .concatenateFragmentedFrames(false)
        .autoPongOnPing(false)
        .ignorePong(true)
        .autoPing(None)
    )
  val wsServerEndpoint = wsEndpoint.handleSuccess(_ => wsPipe)

  val endpoints = genEndpointsId(1)

  def main(args: Array[String]): Unit = {
    val declaredPort = 8080
    val declaredHost = "0.0.0.0"

    supervised {
      val serverBinding: NettySyncServerBinding = useInScope(
        NettySyncServer(NettySyncServerOptions.customiseInterceptors.options)
          .port(declaredPort)
          .host(declaredHost)
          .addEndpoints(wsServerEndpoint :: endpoints)
          .start()
      )(_.stop())
      println(s"Netty running with binding: $serverBinding")
      never
    }
  }
  def genServerEndpoints[F[_]](routeCount: Int)(reply: String => F[String]): List[ServerEndpoint[Any, F]] =
    serverEndpoints[F](reply).flatMap(gen => (0 to routeCount).map(i => gen(i)))
  def genEndpointsId(count: Int): List[ServerEndpoint[Any, Identity]] = genServerEndpoints[Identity](count)(x => x: Identity[String])
}
