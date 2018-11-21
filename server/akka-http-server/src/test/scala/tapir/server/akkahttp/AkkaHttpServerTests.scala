package tapir.server.akkahttp

import cats.implicits._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import cats.effect.{IO, Resource}
import tapir.Endpoint
import tapir.server.tests.ServerTests
import tapir.typelevel.ParamsAsArgs

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class AkkaHttpServerTests extends ServerTests[Future] {

  private implicit var actorSystem: ActorSystem = _
  private implicit var materializer: ActorMaterializer = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    actorSystem = ActorSystem()
    materializer = ActorMaterializer()
  }

  override protected def afterAll(): Unit = {
    Await.result(actorSystem.terminate(), 1.second)
    super.afterAll()
  }

  override def server[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Port, fn: FN[Future[Either[E, O]]])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Resource[IO, Unit] = {

    val bind = IO.fromFuture(IO(Http().bindAndHandle(e.toRoute(fn), "localhost", port)))
    Resource.make(bind)(binding => IO.fromFuture(IO(binding.unbind())).map(_ => ())).map(_ => ())
  }

  override def pureResult[T](t: T): Future[T] = Future.successful(t)
}
