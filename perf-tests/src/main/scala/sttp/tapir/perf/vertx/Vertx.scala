package sttp.tapir.perf.vertx

import cats.effect.IO
import cats.effect.kernel.Resource
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.{Future => VFuture, Vertx}
import io.vertx.ext.web.{Route, Router}
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter
import sttp.tapir.perf.Common._
import scala.concurrent.Future

object Tapir extends Endpoints {
  val serverEndpointGens = replyingWithDummyStr(allEndpoints, Future.successful)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList

  def route: Int => Router => Route = { (nRoutes: Int) => router =>
    val interpreter = VertxFutureServerInterpreter()
    genEndpoints(nRoutes).map(interpreter.route(_)(router)).last
  }
}

object VertxRunner {
  def runServer(route: Router => Route): IO[ServerRunner.KillSwitch] = {
    Resource
      .make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)
      .flatMap { vertx =>
        val router = Router.router(vertx)
        val server = vertx.createHttpServer(new HttpServerOptions().setPort(Port)).requestHandler(router)
        val listenIO = vertxFutureToIo(server.listen(Port))
        route.apply(router): Unit
        Resource.make(listenIO)(s => vertxFutureToIo(s.close()).void)
      }
      .allocated
      .map(_._2)
  }

  private def vertxFutureToIo[A](future: => VFuture[A]): IO[A] =
    IO.async[A] { cb =>
      IO {
        future
          .onFailure { cause => cb(Left(cause)) }
          .onSuccess { result => cb(Right(result)) }
        Some(IO.unit)
      }
    }
}

object TapirServer extends ServerRunner { override def start = VertxRunner.runServer(Tapir.route(1)) }
