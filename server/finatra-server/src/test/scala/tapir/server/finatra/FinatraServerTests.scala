package tapir.server.finatra

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
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

  override def server(routes: NonEmptyList[FinatraRoute], port: Port): Resource[IO, Unit] = ???

  override def initialPort: Port = 8000
}
