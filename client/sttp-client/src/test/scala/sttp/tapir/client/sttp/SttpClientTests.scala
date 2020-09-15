package sttp.tapir.client.sttp

import cats.effect.{Blocker, ContextShift, IO}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

import scala.concurrent.ExecutionContext

class SttpClientTests extends ClientTests[Fs2Streams[IO]](Fs2Streams[IO]) {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  private val backend: SttpBackend[IO, Fs2Streams[IO]] =
    AsyncHttpClientFs2Backend[IO](Blocker.liftExecutionContext(ExecutionContext.Implicits.global)).unsafeRunSync()

  override def mkStream(s: String): fs2.Stream[IO, Byte] = fs2.Stream.emits(s.getBytes("utf-8"))
  override def rmStream(s: fs2.Stream[IO, Byte]): String =
    s.through(fs2.text.utf8Decode)
      .compile
      .foldMonoid
      .unsafeRunSync()

  override def send[I, E, O, FN[_]](e: Endpoint[I, E, O, Fs2Streams[IO]], port: Port, args: I): IO[Either[E, O]] = {
    e.toSttpRequestUnsafe(uri"http://localhost:$port").apply(args).send(backend).map(_.body)
  }

  override def safeSend[I, E, O, FN[_]](
      e: Endpoint[I, E, O, Fs2Streams[IO]],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    e.toSttpRequest(uri"http://localhost:$port").apply(args).send(backend).map(_.body)
  }

  override protected def afterAll(): Unit = {
    backend.close().unsafeRunSync()
    super.afterAll()
  }
}
