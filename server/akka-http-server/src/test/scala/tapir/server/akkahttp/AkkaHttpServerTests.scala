package tapir.server.akkahttp

import cats.implicits._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import tapir.{Endpoint, _}
import tapir.server.tests.ServerTests
import tapir.typelevel.ParamsAsArgs

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class AkkaHttpServerTests extends ServerTests[Future, AkkaStream, Route] {

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

  override def route[I, E, O, FN[_]](e: Endpoint[I, E, O, AkkaStream],
                                     fn: FN[Future[Either[E, O]]],
                                     statusMapper: O => StatusCode,
                                     errorStatusMapper: E => StatusCode)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Route = {

    e.toRoute(fn, statusMapper, errorStatusMapper)
  }

  override def server[I, E, O, FN[_]](routes: NonEmptyList[Route], port: Port): Resource[IO, Unit] = {
    val bind = IO.fromFuture(IO(Http().bindAndHandle(routes.toList.reduce(_ ~ _), "localhost", port)))
    Resource.make(bind)(binding => IO.fromFuture(IO(binding.unbind())).map(_ => ())).map(_ => ())
  }

  override def pureResult[T](t: T): Future[T] = Future.successful(t)
}
