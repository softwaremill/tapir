package sttp.tapir.server.netty.sync

import ox.InScopeRunner
import sttp.capabilities.WebSockets
import sttp.monad.syntax.*
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}
import sttp.tapir.server.netty.internal.{NettyBodyListener, RunAsync}
import sttp.tapir.server.netty.{NettyResponse, NettyServerRequest, Route}

import internal.{NettySyncRequestBody, NettySyncToResponseBody}

trait NettySyncServerInterpreter:
  def nettyServerOptions: NettySyncServerOptions

  def toRoute(
      ses: List[ServerEndpoint[OxStreams & WebSockets, Identity]],
      inScopeRunner: InScopeRunner
  ): IdRoute =
    implicit val bodyListener: BodyListener[Identity, NettyResponse] = new NettyBodyListener(RunAsync.Id)
    val serverInterpreter = new ServerInterpreter[OxStreams with WebSockets, Identity, NettyResponse, OxStreams](
      FilterServerEndpoints(ses),
      new NettySyncRequestBody(nettyServerOptions.createFile),
      new NettySyncToResponseBody(RunAsync.Id, inScopeRunner),
      RejectInterceptor.disableWhenSingleEndpoint(nettyServerOptions.interceptors, ses),
      nettyServerOptions.deleteFile
    )
    val handler: Route[Identity] = { (request: NettyServerRequest) =>
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
