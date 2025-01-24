package sttp.tapir.client.sttp4

import sttp.capabilities.Streams
import sttp.tapir.WebSocketBodyOutput
import sttp.ws.WebSocket

/** Captures the logic of converting a [[WebSocket]] to a interpreter-specific pipe, which is then returned to the client. Implementations
  * of this trait are looked up in the implicit scope by the compiler, depending on the capabilities that are required by the endpoint to be
  * interpreted as a client.
  *
  * For capabilities `R`, where web sockets aren't included, the implementation just throws an unsupported exception (and this logic
  * shouldn't ever be used). For capabilities which include web sockets, appropriate implementations should be imported, e.g. from the
  * `sttp.tapir.client.sttp.ws.fs2._` or `sttp.tapir.client.sttp.ws.akka._` packages.
  */
trait WebSocketToPipe[-R] {
  type S <: Streams[S]
  type F[_]

  /*
  This should be:
  def apply[REQ, RESP](s: S)(ws: WebSocket[F], o: WebSocketBodyOutput[s.Pipe, REQ, RESP, _, S]): s.Pipe[REQ, RESP]

  but this causes:
  java.lang.AbstractMethodError: Receiver class sttp.tapir.client.sttp.ws.fs2.WebSocketToFs2Pipe does not define or
  inherit an implementation of the resolved method 'abstract java.lang.Object apply(sttp.capabilities.package$Streams,
  sttp.ws.WebSocket, sttp.tapir.WebSocketBodyOutput)' of interface sttp.tapir.client.sttp.WebSocketToPipe.

  I have no idea why.
   */
  def apply[REQ, RESP](s: Any)(ws: WebSocket[F], o: WebSocketBodyOutput[Any, REQ, RESP, _, S]): Any
}

object WebSocketToPipe {
  private def notSupported[T]: WebSocketToPipe[T] = new WebSocketToPipe[T] {
    override type S = Nothing
    override type F[X] = X

    override def apply[REQ, RESP](s: Any)(ws: WebSocket[F], o: WebSocketBodyOutput[Any, REQ, RESP, _, Nothing]): Any =
      throw new RuntimeException("WebSockets are not supported")
  }
  // default case; supporting implementations can import
  implicit def webSocketsNotSupported[T]: WebSocketToPipe[T] = notSupported
}
