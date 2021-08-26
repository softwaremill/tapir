package sttp.tapir.server.netty

import java.util.UUID

import scala.concurrent.Future
import scala.util.Random

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model.sse.ServerSentEvent
import sttp.monad.FutureMonad
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

class NettyServerTest extends TestSuite with EitherValues {
  def randomUUID = Some(UUID.randomUUID().toString)
  val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
  val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))

  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource.pure {
        implicit val m: FutureMonad = new FutureMonad()

        val interpreter = new NettyTestServerInterpreter()
        val createServerTest = new DefaultCreateServerTest(backend, interpreter)

        new ServerBasicTests(createServerTest, interpreter).tests() //++
      }
    }
}
