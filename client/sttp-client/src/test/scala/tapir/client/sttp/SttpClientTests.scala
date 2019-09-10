package tapir.client.sttp

import java.nio.ByteBuffer

import cats.effect.{ContextShift, IO}
import cats.implicits._
import tapir.Endpoint
import tapir.client.tests.ClientTests
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.fs2.AsyncHttpClientFs2Backend

import scala.concurrent.ExecutionContext

class SttpClientTests extends ClientTests[fs2.Stream[IO, ByteBuffer]] {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  private implicit val backend: SttpBackend[IO, fs2.Stream[IO, ByteBuffer]] = AsyncHttpClientFs2Backend[IO]()

  override def mkStream(s: String): fs2.Stream[IO, ByteBuffer] = fs2.Stream.emits(s.getBytes("utf-8")).map(b => ByteBuffer.wrap(Array(b)))
  override def rmStream(s: fs2.Stream[IO, ByteBuffer]): String =
    s.map(bb => fs2.Chunk.array(bb.array))
      .through(fs2.text.utf8DecodeC)
      .compile
      .foldMonoid
      .unsafeRunSync()

  override def send[I, E, O, FN[_]](e: Endpoint[I, E, O, fs2.Stream[IO, ByteBuffer]], port: Port, args: I): IO[Either[E, O]] = {
    e.toSttpRequest(uri"http://localhost:$port").apply(args).send().map(_.unsafeBody)
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
