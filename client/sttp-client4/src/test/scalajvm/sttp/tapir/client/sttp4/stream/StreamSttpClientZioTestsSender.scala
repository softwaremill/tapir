package sttp.tapir.client.sttp4.stream

import cats.effect.IO
import sttp.capabilities.zio.ZioStreams
import sttp.client4._
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.tapir.client.tests.ClientTests
import sttp.tapir.{DecodeResult, Endpoint}
import zio.Runtime.default
import zio.{CancelableFuture, Task, Unsafe}

abstract class StreamSttpClientZioTestsSender extends ClientTests[ZioStreams] {
  private val runtime: default.UnsafeAPI = zio.Runtime.default.unsafe
  val backend: StreamBackend[Task, ZioStreams] = unsafeRun(HttpClientZioBackend())

  override def send[A, I, E, O](
      e: Endpoint[A, I, E, O, ZioStreams],
      port: Port,
      securityArgs: A,
      args: I,
      scheme: String = "http"
  ): IO[Either[E, O]] =
    IO.fromFuture(IO.delay {
      val send = StreamSttpClientInterpreter()
        .toSecureRequestThrowDecodeFailures[A, I, E, O, ZioStreams](e, Some(uri"$scheme://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send[Task, ZioStreams](backend)
        .map(_.body)

      unsafeToFuture(send).future
    })

  override def safeSend[A, I, E, O](
      e: Endpoint[A, I, E, O, ZioStreams],
      port: Port,
      securityArgs: A,
      args: I
  ): IO[DecodeResult[Either[E, O]]] =
    IO.fromFuture(IO.delay {
      val send = StreamSttpClientInterpreter()
        .toSecureRequest[A, I, E, O, ZioStreams](e, Some(uri"http://localhost:$port"))
        .apply(securityArgs)
        .apply(args)
        .send[Task, ZioStreams](backend)
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
