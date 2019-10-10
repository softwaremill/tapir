package tapir.server.akkahttp

import cats.implicits._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.softwaremill.sttp._
import com.typesafe.scalalogging.StrictLogging
import tapir.{Endpoint, endpoint, stringBody}
import tapir.server.tests.ServerTests
import tapir._
import tapir.server.{DecodeFailureHandler, ServerDefaults}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

class AkkaHttpServerTests extends ServerTests[Future, AkkaStream, Route] with StrictLogging {

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

  override def route[I, E, O](
      e: Endpoint[I, E, O, AkkaStream],
      fn: I => Future[Either[E, O]],
      decodeFailureHandler: Option[DecodeFailureHandler[Any]] = None
  ): Route = {
    implicit val serverOptions: AkkaHttpServerOptions = AkkaHttpServerOptions.default.copy(
      decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)
    )
    e.toRoute(fn)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, AkkaStream], fn: I => Future[O])(
      implicit eClassTag: ClassTag[E]
  ): Route = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[Route], port: Port): Resource[IO, Unit] = {
    val bind = IO(logger.info(s"Trying to bind to $port")) >> IO.fromFuture(
      IO(Http().bindAndHandle(routes.toList.reduce(_ ~ _), "localhost", port)).onError {
        case e: Exception => IO(logger.error(s"Bind to port $port failed because of ${e.getMessage}"))
      }
    )
    Resource.make(bind)(binding => IO.fromFuture(IO(binding.unbind())).void).void
  }

  override def pureResult[T](t: T): Future[T] = Future.successful(t)
  override def suspendResult[T](t: => T): Future[T] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future { t }
  }

  override val initialPort: Port = 32000

  if (testNameFilter.isEmpty) {
    test("endpoint nested in a path directive") {
      val e = endpoint.get.in("test" and "directive").out(stringBody).serverLogic(_ => pureResult("ok".asRight[Unit]))
      val port = nextPort()
      val route = Directives.pathPrefix("api")(e.toRoute)
      server(NonEmptyList.of(route), port).use { _ =>
        sttp.get(uri"http://localhost:$port/api/test/directive").send().map(_.body shouldBe Right("ok"))
      }.unsafeRunSync
    }
  }
}
