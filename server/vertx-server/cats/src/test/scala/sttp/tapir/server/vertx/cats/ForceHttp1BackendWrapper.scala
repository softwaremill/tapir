package sttp.tapir.server.vertx.cats

import sttp.client4._
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.HttpVersion
import sttp.monad.MonadError
import sttp.capabilities.Effect
import sttp.capabilities.WebSockets

class ForceHttp1BackendWrapper[F[_]](delegate: WebSocketStreamBackend[F, Fs2Streams[F]]) extends WebSocketStreamBackend[F, Fs2Streams[F]] {
  override def send[T](request: GenericRequest[T, Fs2Streams[F] with WebSockets with Effect[F]]): F[Response[T]] =
    delegate.send(request.httpVersion(HttpVersion.HTTP_1_1))

  override def close(): F[Unit] = delegate.close()

  override def monad: MonadError[F] = delegate.monad
}
