package sttp.tapir.client.sttp4

import sttp.client4._
import sttp.shared.Identity

object GenericRequestExtensions {

  def sendRequest[F[_], T](backend: Backend[F], genReq: GenericRequest[T, _]): F[Response[T]] = {
    (genReq, backend) match {
      case (r: Request[_], b: Backend[_]) => r.asInstanceOf[Request[T]].send(b)
      case (r: WebSocketRequest[_, _], b: WebSocketBackend[F]) =>
        r.asInstanceOf[WebSocketRequest[F, T]].send(b.asInstanceOf[WebSocketBackend[F]])
      case (r: WebSocketStreamRequest[_, _], b: WebSocketStreamBackend[F, _]) =>
        r.asInstanceOf[WebSocketStreamRequest[T, Any]].send(b.asInstanceOf[WebSocketStreamBackend[F, Any]])
      case (r: StreamRequest[_, _], b: StreamBackend[F, _]) =>
        r.asInstanceOf[StreamRequest[T, Any]].send(b.asInstanceOf[StreamBackend[F, Any]])
      case other =>
        throw new RuntimeException(
          s"Unsupported request/backend pair. request: ${other._1}, backend: ${other._2}"
        )
    }
  }

  def sendRequest[T](backend: SyncBackend, genReq: GenericRequest[T, _]): Response[T] = {
    (genReq, backend) match {
      case (r: Request[_], b: SyncBackend)                      => r.send(b)
      case (r: WebSocketRequest[_, _], b: WebSocketSyncBackend) => r.asInstanceOf[WebSocketRequest[Identity, T]].send(b)
      case other => throw new RuntimeException(s"Unsupported request/backend pair. request: ${other._1}, backend: ${other._2}")
    }
  }

}
