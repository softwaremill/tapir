package sttp.tapir.client.sttp4

import cats.effect.IO
import sttp.client4._
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}
import zio.Runtime.default
import zio.{CancelableFuture, Task, Unsafe}

abstract class SttpClientZioTestsSender extends ClientTests[Any] {
  private val runtime: default.UnsafeAPI = zio.Runtime.default.unsafe
  val backend: Backend[Task] = unsafeRun(HttpClientZioBackend())

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] =
    IO.fromFuture(IO.delay {
      val send = SttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures[A, I, E, O](e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send(backend)
        .map(_.body)

      unsafeToFuture(send).future
    })

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] =
    IO.fromFuture(IO.delay {
      val send = SttpClientInterpreter()
        .toSecureRequest[A, I, E, O](e, Some(uri"http://localhost:$port"))
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
