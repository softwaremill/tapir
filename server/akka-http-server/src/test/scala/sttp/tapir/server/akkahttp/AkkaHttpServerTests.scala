package sttp.tapir.server.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.Flow
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.monad.FutureMonad
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.server.tests.{
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerStreamingTests,
  ServerTests,
  ServerWebSocketTests,
  backendResource
}
import sttp.tapir.tests.{Test, TestSuite}

class AkkaHttpServerTests extends TestSuite {

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)
      val interpreter = new AkkaServerInterpreter()(actorSystem)
      val serverTests = new ServerTests(interpreter)

      def additionalTests(): List[Test] = List(
        Test("endpoint nested in a path directive") {
          val e = endpoint.get.in("test" and "directive").out(stringBody).serverLogic(_ => ("ok".asRight[Unit]).unit)
          val route = Directives.pathPrefix("api")(e.toRoute)
          interpreter
            .server(NonEmptyList.of(route))
            .use { port =>
              basicRequest.get(uri"http://localhost:$port/api/test/directive").send(backend).map(_.body shouldBe Right("ok"))
            }
            .unsafeRunSync()
        }
      )

      new ServerBasicTests(backend, serverTests, interpreter).tests() ++
        new ServerStreamingTests(backend, serverTests, AkkaStreams).tests() ++
        new ServerWebSocketTests(backend, serverTests, AkkaStreams) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
        }.tests() ++
        new ServerAuthenticationTests(backend, serverTests).tests() ++
        additionalTests()
    }
  }
}
