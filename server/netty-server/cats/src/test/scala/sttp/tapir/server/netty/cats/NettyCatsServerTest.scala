package sttp.tapir.server.netty.cats

import cats.effect.{IO, Resource}
import cats.syntax.all._
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import sttp.model.Part
import sttp.model.StatusCode
import org.scalatest.matchers.should.Matchers
import sttp.tapir.tests.data.FruitAmount

class NettyCatsServerTest extends TestSuite with EitherValues with Matchers {

  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make {
          implicit val m: MonadError[IO] = new CatsMonadError[IO]()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyCatsTestServerInterpreter(eventLoopGroup, dispatcher)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)
          val ioSleeper: Sleeper[IO] = new Sleeper[IO] {
            override def sleep(duration: FiniteDuration): IO[Unit] = IO.sleep(duration)
          }
          def drainFs2(stream: Fs2Streams[IO]#BinaryStream): IO[Unit] =
            stream.compile.drain.void

          import createServerTest._
          import sttp.tapir.tests.Multipart._
          import sttp.tapir.tests.TestUtil.{readFromFile, writeToFile}
          import sttp.client3.{multipartFile, _}
          val tests = new AllServerTests(
            createServerTest,
            interpreter,
            backend,
            multipart = false
          )
            .tests() ++
            new ServerStreamingTests(createServerTest).tests(Fs2Streams[IO])(drainFs2) ++
            new ServerCancellationTests(createServerTest)(m, IO.asyncForIO).tests() ++
            new NettyFs2StreamingCancellationTest(createServerTest).tests() ++
            new ServerGracefulShutdownTests(createServerTest, ioSleeper).tests() ++
            new ServerWebSocketTests(
              createServerTest,
              Fs2Streams[IO],
              autoPing = true,
              failingPipe = true,
              handlePong = true
            ) {
              override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
              override def emptyPipe[A, B]: fs2.Pipe[IO, A, B] = _ => fs2.Stream.empty
            }.tests() ++ List(
              testServer(in_raw_multipart_out_string, "multi1")((parts: Seq[Part[Array[Byte]]]) =>
                pureResult(
                  Right(parts.map(part => s"${part.name}:${new String(part.body)}").mkString("\n"))
                )
              ) { (backend, baseUri) =>
                val file1 = writeToFile("peach mario")
                val file2 = writeToFile("daisy luigi")
                basicStringRequest
                  .post(uri"$baseUri/api/echo/multipart")
                  .multipartBody(
                    multipartFile("file1", file1).fileName("file1.txt"),
                    multipartFile("file2", file2).fileName("file2.txt")
                  )
                  .send(backend)
                  .map { r =>
                    r.code shouldBe StatusCode.Ok
                    r.body should include("file1:peach mario")
                    r.body should include("file2:daisy luigi")
                  }
              },
              testServer(in_simple_multipart_out_string, "multi2")((fa: FruitAmount) =>
                pureResult(fa.toString.asRight[Unit])
              ) { (backend, baseUri) =>
                basicStringRequest
                  .post(uri"$baseUri/api/echo/multipart")
                  .multipartBody(multipart("fruit", "pineapple"), multipart("amount", "120"), multipart("shape", "regular"))
                  .send(backend)
                  .map { r =>
                    r.body shouldBe "FruitAmount(pineapple,120)"
                  }
              }
            )

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_])).void
        }
        .map { case (tests, _) => tests }
    }
}
