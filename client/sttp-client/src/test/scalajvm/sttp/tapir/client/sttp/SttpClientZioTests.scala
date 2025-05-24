package sttp.tapir.client.sttp

import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.tapir.client.tests.ClientTestsIO
import sttp.tapir.{DecodeResult, Endpoint}
import zio.Runtime.default
import zio.{CancelableFuture, Task, Unsafe}

abstract class SttpClientZioTests[R >: WebSockets with ZioStreams] extends ClientTestsIO[R] {
  private val runtime: default.UnsafeAPI = zio.Runtime.default.unsafe
  val backend: SttpBackend[Task, R] = unsafeRun(HttpClientZioBackend())
  def wsToPipe: WebSocketToPipe[R]

  override def sendIO[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] =
    IO.fromFuture(IO.delay {
      implicit val wst: WebSocketToPipe[R] = wsToPipe
      val send = SttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures(e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
      unsafeToFuture(send).future
    })

  override def safeSendIO[A, I, E, O](
      e: Endpoint[A, I, E, O, R],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] =
    IO.fromFuture(IO.delay {
      implicit val wst: WebSocketToPipe[R] = wsToPipe
      val send = SttpClientInterpreter()
        .toSecureRequest(e, Some(uri"http://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)
      unsafeToFuture(send).future
    })

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
