package sttp.tapir.server.pekkohttp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.server.{Directives, RequestContext}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.pekko.PekkoStreams
import sttp.client3._
import sttp.client3.pekkohttp.PekkoHttpBackend
import sttp.model.sse.ServerSentEvent
import sttp.monad.FutureMonad
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.server.interceptor._
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class PekkoHttpServerTest extends TestSuite with EitherValues {
  private def randomUUID = Some(UUID.randomUUID().toString)
  private val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
  private val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)

      val interpreter = new PekkoHttpTestServerInterpreter()(actorSystem)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      def additionalTests(): List[Test] = List(
        Test("endpoint nested in a path directive") {
          val e = endpoint.get.in("test" and "directive").out(stringBody).serverLogic(_ => ("ok".asRight[Unit]).unit)
          val route = Directives.pathPrefix("api")(PekkoHttpServerInterpreter().toRoute(e))
          interpreter
            .server(NonEmptyList.of(route))
            .use { port =>
              basicRequest.get(uri"http://localhost:$port/api/test/directive").send(backend).map(_.body shouldBe Right("ok"))
            }
            .unsafeToFuture()
        },
        Test("Send and receive SSE") {
          implicit val ec = actorSystem.dispatcher
          val e = endpoint.get
            .in("sse")
            .out(serverSentEventsBody)
            .serverLogicSuccess[Future](_ => {
              Future.successful(Source(List(sse1, sse2)))
            })
          val route = PekkoHttpServerInterpreter().toRoute(e)
          interpreter
            .server(NonEmptyList.of(route))
            .use { port =>
              IO.fromFuture {
                IO(
                  basicRequest
                    .get(uri"http://localhost:$port/sse")
                    .response(
                      asStreamUnsafe(PekkoStreams).mapRight(stream =>
                        PekkoServerSentEvents.parseBytesToSSE(stream).runFold(List.empty[ServerSentEvent])((acc, sse) => acc :+ sse)
                      )
                    )
                    .send(PekkoHttpBackend.usingActorSystem(actorSystem))
                    .flatMap(_.body.value.transform(sse => sse shouldBe List(sse1, sse2), ex => fail(ex)))
                )
              }
            }
            .unsafeToFuture()
        },
        Test("replace body using a request interceptor") {
          val e = endpoint.post.in(stringBody).out(stringBody).serverLogicSuccess[Future](body => Future.successful(body))

          val route = PekkoHttpServerInterpreter(
            PekkoHttpServerOptions.customiseInterceptors
              .prependInterceptor(RequestInterceptor.transformServerRequest { request =>
                val underlying = request.underlying.asInstanceOf[RequestContext]
                val changedUnderlying = underlying.withRequest(underlying.request.withEntity(HttpEntity("replaced")))
                Future.successful(request.withUnderlying(changedUnderlying))
              })
              .options
          ).toRoute(e)

          interpreter
            .server(NonEmptyList.of(route))
            .use { port =>
              basicRequest.post(uri"http://localhost:$port").body("test123").send(backend).map(_.body shouldBe Right("replaced"))
            }
            .unsafeToFuture()
        }
      )
      def drainPekko(stream: PekkoStreams.BinaryStream): Future[Unit] =
        stream.runWith(Sink.ignore).map(_ => ())

      new AllServerTests(createServerTest, interpreter, backend).tests() ++
        new ServerStreamingTests(createServerTest).tests(PekkoStreams)(drainPekko) ++
        new ServerWebSocketTests(createServerTest, PekkoStreams, autoPing = false, failingPipe = true, handlePong = false) {
          override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
          override def emptyPipe[A, B]: Flow[A, B, Any] = Flow.fromSinkAndSource(Sink.ignore, Source.empty)
        }.tests() ++
        additionalTests()
    }
  }
}
