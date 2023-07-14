package sttp.tapir.server.netty.cats

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.netty.internal.FutureUtil
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import fs2.io.file.Files
import java.nio.file.Paths
import fs2.io.file.Path
import scala.util.Random

class NettyCatsServerTest extends TestSuite with EitherValues with StrictLogging with Matchers {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make {
          implicit val m: MonadError[IO] = new CatsMonadError[IO]()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyCatsTestServerInterpreter(eventLoopGroup, dispatcher)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          val tests = new AllServerTests(createServerTest, interpreter, backend, multipart = false).tests() 
          //
          // val tests = new ServerStreamingTests(createServerTest, Fs2Streams[IO]).tests()

          val s = Fs2Streams[IO]
          val streamBody = streamTextBody(s)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8))

          val responseStr = "This is response text"
          val responseText = responseStr.getBytes().toList
          // val responseAsStream = Files[IO].readAll(Path.fromNioPath(Paths.get("/home/kc/LICENSE")))
          val responseAsStream: fs2.Stream[IO, Byte] = fs2.Stream.emits(responseText).covary[IO]
          val testRoute = endpoint.post.in("kc").in(streamBody).out(streamBody).serverLogic[IO] { (inputStream) =>
            val sink = Files[IO].writeAll(Path.fromNioPath(Paths.get(s"./out-${Random.nextInt()}")))
            // val s2 = inputStream.through(sink).compile
            // s2.drain.unsafeRunSync()

            IO.delay(Right(inputStream.map(_.toChar).map(_.toString).map(_.toUpperCase).map(_.head).map(_.toByte)))
            // IO.delay(Right(fs2.Stream.emits(inputStream.getBytes().toList)))
          }

          val route = interpreter.route(testRoute)
          val rs = NonEmptyList.one(route)
          val resources = for {
            port <- interpreter.server(rs).onError { case e: Exception =>
              Resource.eval(IO(logger.error(s"Starting server failed because of ${e.getMessage}")))
            }
            _ <- Resource.eval(IO(logger.info(s"Bound server on port: $port")))
          } yield port

          val requestStr = "Pen pineapple apple pen streaming req text"
          val requestBytes = requestStr.getBytes().toList
          val requestAsStream: fs2.Stream[IO, Byte] = fs2.Stream.emits(requestBytes).covary[IO]
          val tests2 = List(Test("work!") {
            resources
              .use { port =>
                val baseUri = uri"http://localhost:$port"
                // IO.sleep(30.seconds) >>
                basicRequest
                  .post(uri"$baseUri/kc")
                  .streamBody(Fs2Streams[IO])(requestAsStream)
                  // .body("requestStr")
                  .send(backend)
                  .map(_.body shouldBe Right(responseStr))
              }
              .unsafeToFuture()
          })

          IO.pure((tests, eventLoopGroup))
        } { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(FutureUtil.nettyFutureToScala(eventLoopGroup.shutdownGracefully()))).void
        }
        .map { case (tests, _) => tests }
    }
}
