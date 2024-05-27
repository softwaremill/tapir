package sttp.tapir.server.netty.sync

import internal.{NettySyncRequestBody, NettySyncToResponseBody}
import internal.ox.OxDispatcher
import sttp.capabilities.WebSockets
import sttp.monad.syntax._
import sttp.tapir.Id
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.netty.internal.{NettyBodyListener, RunAsync}
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, Route}

trait NettySyncServerInterpreter:
  def nettyServerOptions: NettySyncServerOptions

  /** Requires implicit supervision scope (Ox), because it needs to know in which scope it can start background forks in the Web Sockets
    * processor.
    */
  def toRoute(
      ses: List[ServerEndpoint[OxStreams & WebSockets, Id]],
      oxDispatcher: OxDispatcher
  ): IdRoute =
    implicit val bodyListener: BodyListener[Id, NettyResponse] = new NettyBodyListener(RunAsync.Id)
    val serverInterpreter = new ServerInterpreter[OxStreams with WebSockets, Id, NettyResponse, OxStreams](
      FilterServerEndpoints(ses),
      new NettySyncRequestBody(nettyServerOptions.createFile),
      new NettySyncToResponseBody(RunAsync.Id, oxDispatcher),
      RejectInterceptor.disableWhenSingleEndpoint(nettyServerOptions.interceptors, ses),
      nettyServerOptions.deleteFile
    )
    val handler: Route[Id] = { (request: NettyServerRequest) =>
      serverInterpreter(request)
        .map {
          case RequestResult.Response(response) => Some(response)
          case RequestResult.Failure(_)         => None
        }
    }
    handler

object NettySyncServerInterpreter:
  def apply(serverOptions: NettySyncServerOptions = NettySyncServerOptions.default): NettySyncServerInterpreter =
    new NettySyncServerInterpreter {
      override def nettyServerOptions: NettySyncServerOptions = serverOptions
    }
