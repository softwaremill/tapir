package sttp.tapir.server.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.{Directives, RequestContext}
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
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
import sttp.tapir.server.interceptor._
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}

import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

class AkkaHttpServerTest extends TestSuite with EitherValues {
  private def randomUUID = Some(UUID.randomUUID().toString)
  private val sse1 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))
  private val sse2 = ServerSentEvent(randomUUID, randomUUID, randomUUID, Some(Random.nextInt(200)))

  def actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO.delay(ActorSystem()))(actorSystem => IO.fromFuture(IO.delay(actorSystem.terminate())).void)

  override def tests: Resource[IO, List[Test]] = backendResource.flatMap { backend =>
    actorSystemResource.map { implicit actorSystem =>
      implicit val m: FutureMonad = new FutureMonad()(actorSystem.dispatcher)

      val interpreter = new AkkaHttpTestServerInterpreter()(actorSystem)
      val createServerTest = new DefaultCreateServerTest(backend, interpreter)

      def additionalTests(): List[Test] = List(
        Test("endpoint nested in a path directive") {
          val e = endpoint.get.in("test" and "directive").out(stringBody).serverLogic(_ => ("ok".asRight[Unit]).unit)
          val route = Directives.pathPrefix("api")(AkkaHttpServerInterpreter().toRoute(e))
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
          val route = AkkaHttpServerInterpreter().toRoute(e)
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
            .unsafeToFuture()
        },
        Test("replace body using a request interceptor") {
          val e = endpoint.post.in(stringBody).out(stringBody).serverLogicSuccess[Future](body => Future.successful(body))

          val route = AkkaHttpServerInterpreter(
            AkkaHttpServerOptions.customiseInterceptors
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
     new ServerFilesTests(interpreter, backend).tests() 
      // new AllServerTests(createServerTest, interpreter, backend).tests() ++
      //   new ServerStreamingTests(createServerTest, AkkaStreams).tests() ++
      //   new ServerWebSocketTests(createServerTest, AkkaStreams) {
      //     override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
      //     override def emptyPipe[A, B]: Flow[A, B, Any] = Flow.fromSinkAndSource(Sink.ignore, Source.empty)
      //   }.tests() ++
      //   additionalTests()
    }
  }
}
