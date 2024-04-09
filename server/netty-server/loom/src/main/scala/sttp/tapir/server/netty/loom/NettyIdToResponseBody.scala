package sttp.tapir.server.netty.loom

import sttp.tapir.server.netty.internal.NettyToResponseBody
import sttp.monad.MonadError
import sttp.tapir.server.netty.internal.RunAsync

class NettyIdToResponseBody(runAsync: RunAsync[Id])(implicit me: MonadError[Id]) extends NettyToResponseBody[Id](runAsync)(me) {}
