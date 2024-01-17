package sttp.tapir.perf.vertx

import cats.effect.IO
import cats.effect.kernel.Resource
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.{Future => VFuture, Vertx}
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter

import java.util.Date
import scala.concurrent.Future
import scala.util.Random

object Tapir extends Endpoints {
  val serverEndpointGens = replyingWithDummyStr(allEndpoints, Future.successful)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList

  def route: Int => Router => Route = { (nRoutes: Int) => router =>
    val interpreter = VertxFutureServerInterpreter()
    genEndpoints(nRoutes).map(interpreter.route(_)(router)).last
  }
}
object Vanilla extends Endpoints {

  def bodyHandler = BodyHandler.create(false).setBodyLimit(LargeInputSize + 100L)

  def route: Int => Router => Route = { (nRoutes: Int) => router =>
    (0 until nRoutes).map { number =>
      router.get(s"/path$number/4").handler {
        ctx: RoutingContext =>
          val _ = ctx
            .response()
            .putHeader("content-type", "text/plain")
            .end("Ok")
      }

      router.post(s"/path$number/4").handler(bodyHandler).handler {
        ctx: RoutingContext =>
          val body = ctx.body.asString()
          val _ = ctx
            .response()
            .putHeader("content-type", "text/plain")
            .end(s"Ok, body length = ${body.length}")
      }

      router.post(s"/pathBytes$number/4").handler(bodyHandler).handler {
        ctx: RoutingContext =>
          val bytes = ctx.body().asString()
          val _ = ctx
            .response()
            .putHeader("content-type", "text/plain")
            .end(s"Received ${bytes.length} bytes")
      }

      router.post(s"/pathFile$number/4").handler(bodyHandler).handler {
        ctx: RoutingContext =>
          val filePath = s"${TmpDir.getAbsolutePath}/tapir-${new Date().getTime}-${Random.nextLong()}"
          val fs = ctx.vertx.fileSystem
          val _ = fs
            .createFile(filePath)
            .flatMap(_ => fs.writeFile(filePath, ctx.body().buffer()))
            .flatMap(_ =>
              ctx
                .response()
                .putHeader("content-type", "text/plain")
                .end(s"Received binary stored as: ${filePath}")
            )
      }
    }.last
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
object TapirMultiServer extends ServerRunner { override def start = VertxRunner.runServer(Tapir.route(127)) }
object VanillaServer extends ServerRunner { override def start = VertxRunner.runServer(Vanilla.route(1)) }
object VanillaMultiServer extends ServerRunner { override def start = VertxRunner.runServer(Vanilla.route(127)) }
