package sttp.tapir.client.sttp4.ws

import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client4._
import sttp.client4.impl.zio.FetchZioBackend
import _root_.zio.Runtime.default
import _root_.zio.{CancelableFuture, Task, Unsafe}
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}

abstract class WebSocketSttpClientZioTestsSender extends ClientTests[WebSockets with ZioStreams] {
  private val runtime: default.UnsafeAPI = default.unsafe
  val backend = FetchZioBackend()
  def wsToPipe: WebSocketToPipe[WebSockets with ZioStreams]

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets with ZioStreams],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] = {
    implicit val wst: WebSocketToPipe[WebSockets with ZioStreams] = wsToPipe
    IO.fromFuture(IO.delay {
      val send = WebSocketSttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures[Task, A, I, E, O, WebSockets with ZioStreams](e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
      unsafeToFuture(send).future
    })
  }

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, WebSockets with ZioStreams],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    implicit val wst: WebSocketToPipe[WebSockets with ZioStreams] = wsToPipe
    IO.fromFuture(IO.delay {
      val send = WebSocketSttpClientInterpreter()
        .toSecureRequest(e, Some(uri"http://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
      unsafeToFuture(send).future
    })
  }

  override protected def afterAll(): Unit = {
    unsafeRun(backend.close())
    super.afterAll()
  }

  def unsafeRun[T](task: Task[T]): T =
    Unsafe.unsafe { implicit u =>
      runtime.run(task).getOrThrowFiberFailure()
    }

  def unsafeToFuture[T](task: Task[T]): CancelableFuture[T] =
    Unsafe.unsafe(implicit u => runtime.runToFuture(task))

}
