package sttp.tapir.client.sttp

import cats.effect.{ContextShift, IO}
import sttp.tapir.{DecodeResult, Endpoint}
import sttp.tapir.client.tests.ClientTests
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

import scala.concurrent.ExecutionContext

class SttpClientTests extends ClientTests[fs2.Stream[IO, Byte]] {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  private implicit val backend: SttpBackend[IO, fs2.Stream[IO, Byte], NothingT] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

  override def mkStream(s: String): fs2.Stream[IO, Byte] = fs2.Stream.emits(s.getBytes("utf-8"))
  override def rmStream(s: fs2.Stream[IO, Byte]): String =
    s.through(fs2.text.utf8Decode)
      .compile
      .foldMonoid
      .unsafeRunSync()

  override def send[I, E, O, FN[_]](e: Endpoint[I, E, O, fs2.Stream[IO, Byte]], port: Port, args: I): IO[Either[E, O]] = {
    e.toSttpRequestUnsafe(uri"http://localhost:$port").apply(args).send().map(_.body)
  }

  override def safeSend[I, E, O, FN[_]](
      e: Endpoint[I, E, O, fs2.Stream[IO, Byte]],
      port: Port,
      args: I
  ): IO[DecodeResult[Either[E, O]]] = {
    e.toSttpRequest(uri"http://localhost:$port").apply(args).send().map(_.body)
  }

  override protected def afterAll(): Unit = {
    backend.close().unsafeRunSync()
    super.afterAll()
  }
}
