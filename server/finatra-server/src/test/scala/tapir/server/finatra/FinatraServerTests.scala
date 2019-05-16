package tapir.server.finatra

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.twitter.finatra.http.{Controller, EmbeddedHttpServer, HttpServer}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.util.{Future, FuturePool}
import tapir.Endpoint
import tapir.server.tests.ServerTests
import scala.reflect.ClassTag

class FinatraServerTests extends ServerTests[Future, Nothing, FinatraRoute] {
  private val futurePool = FuturePool.unboundedPool

  override def pureResult[T](t: T): Future[T] = Future.value(t)

  override def suspendResult[T](t: => T): Future[T] = futurePool {
    t
  }

  override def route[I, E, O](e: Endpoint[I, E, O, Nothing], fn: I => Future[Either[E, O]]): FinatraRoute = {
    e.toRoute(fn)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Nothing], fn: I => Future[O])(
      implicit eClassTag: ClassTag[E]
  ): FinatraRoute = ???

  override def server(routes: NonEmptyList[FinatraRoute], port: Port): Resource[IO, Unit] = {
    val bind = IO {
      class TestController extends Controller with TapirController {
        routes.toList.foreach(addTapirRoute)
      }

      class TestServer extends HttpServer {
        override protected def configureHttp(router: HttpRouter): Unit = {
          router.add(new TestController)
        }
      }

      val server = new EmbeddedHttpServer(
        new TestServer,
        Map(
          "http.port" -> s":$port"
        )
      )
      server.start()
      server
    }

    Resource
      .make(bind)(httpServer => IO(httpServer.close()))
      .map(_ => ())
  }

  override def initialPort: Port = 8000
}
