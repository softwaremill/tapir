package sttp.tapir.server.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.{Flow, Source}
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.model.sse.ServerSentEvent
import sttp.monad.FutureMonad
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.server.tests.{
  CreateServerTest,
  ServerAuthenticationTests,
  ServerBasicTests,
  ServerStreamingTests,
  ServerWebSocketTests,
  backendResource
}
import sttp.tapir.tests.{Test, TestSuite}

import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class AkkaHttpCreateServerTest extends TestSuite with EitherValues {
  def randomUUID = Some(UUID.randomUUID().toString)
  val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
  val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)
      val interpreter = new AkkaHttpTestServerInterpreter()(actorSystem)
      val createServerTest = new CreateServerTest(interpreter)

      def additionalTests(): List[Test] = List(
        Test("endpoint nested in a path directive") {
          val e = endpoint.get.in("test" and "directive").out(stringBody).serverLogic(_ => ("ok".asRight[Unit]).unit)
          val route = Directives.pathPrefix("api")(AkkaHttpServerInterpreter.toRoute(e))
          interpreter
            .server(NonEmptyList.of(route))
            .use { port =>
              basicRequest.get(uri"http://localhost:$port/api/test/directive").send(backend).map(_.body shouldBe Right("ok"))
            }
            .unsafeRunSync()
        },
        Test("Send and receive SSE") {
          implicit val ec = actorSystem.dispatcher
          val e = endpoint.get
            .in("sse")
            .out(serverSentEventsBody)
            .serverLogic[Future](_ => {
              Source(List(sse1, sse2)).asRight[Unit].unit(new FutureMonad())
            })
          val route = AkkaHttpServerInterpreter.toRoute(e)
          interpreter
            .server(NonEmptyList.of(route))
            .use { port =>
              IO.fromFuture {
                IO(
                  basicRequest
                    .get(uri"http://localhost:$port/sse")
                    .response(
                      asStreamUnsafe(AkkaStreams).mapRight(stream =>
                        AkkaServerSentEvents.parseBytesToSSE(stream).runFold(List.empty[ServerSentEvent])((acc, sse) => acc :+ sse)
                      )
                    )
                    .send(AkkaHttpBackend.usingActorSystem(actorSystem))
                    .flatMap(_.body.value.transform(sse => sse shouldBe List(sse1, sse2), ex => fail(ex)))
                )
              }
            }
            .unsafeRunSync()
        }
      )

      new ServerBasicTests(backend, createServerTest, interpreter).tests() ++
        new ServerStreamingTests(backend, createServerTest, AkkaStreams).tests() ++
        new ServerWebSocketTests(backend, createServerTest, AkkaStreams) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
        }.tests() ++
        new ServerAuthenticationTests(backend, createServerTest).tests() ++
        additionalTests()
    }
  }
}
