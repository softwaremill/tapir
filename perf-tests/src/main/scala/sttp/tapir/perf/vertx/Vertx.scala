package sttp.tapir.perf.vertx

import _root_.cats.effect.IO
import _root_.cats.effect.kernel.Resource
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.{Future => VFuture, Vertx}
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter
import sttp.tapir.server.vertx.VertxFutureServerOptions

object Tapir extends Endpoints {
  def route(nRoutes: Int, withServerLog: Boolean = false): Router => Route = { router =>
    val serverOptions = buildOptions(VertxFutureServerOptions.customiseInterceptors, withServerLog)
    val interpreter = VertxFutureServerInterpreter(serverOptions)
    genEndpointsFuture(nRoutes).map(interpreter.route(_)(router)).last
  }
}
object Vanilla extends Endpoints {

  def bodyHandler = BodyHandler.create(false).setBodyLimit(LargeInputSize + 100L)

  def route: Int => Router => Route = { (nRoutes: Int) => router =>
    (0 until nRoutes).map { n =>
      router.get(s"/path$n/:id").handler {
        ctx: RoutingContext =>
          val id = ctx.request().getParam("id").toInt
          val _ = ctx
            .response()
            .putHeader("content-type", "text/plain")
            .end(s"${id + n}")
      }

      router.post(s"/path$n").handler(bodyHandler).handler {
        ctx: RoutingContext =>
          val body = ctx.body.asString()
          val _ = ctx
            .response()
            .putHeader("content-type", "text/plain")
            .end(s"Ok [$n], string length = ${body.length}")
      }

      router.post(s"/pathBytes$n").handler(bodyHandler).handler {
        ctx: RoutingContext =>
          val bytes = ctx.body().asString()
          val _ = ctx
            .response()
            .putHeader("content-type", "text/plain")
            .end(s"Ok [$n], bytes length = ${bytes.length}")
      }

      router.post(s"/pathFile$n").handler(bodyHandler).handler {
        ctx: RoutingContext =>
          val filePath = newTempFilePath()
          val fs = ctx.vertx.fileSystem
          val _ = fs
            .createFile(filePath.toString)
            .flatMap(_ => fs.writeFile(filePath.toString, ctx.body().buffer()))
            .flatMap(_ =>
              ctx
                .response()
                .putHeader("content-type", "text/plain")
                .end(s"Ok [$n], file saved to $filePath")
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
object TapirMultiServer extends ServerRunner { override def start = VertxRunner.runServer(Tapir.route(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def start = VertxRunner.runServer(Tapir.route(128, withServerLog = true))
}
object VanillaServer extends ServerRunner { override def start = VertxRunner.runServer(Vanilla.route(1)) }
object VanillaMultiServer extends ServerRunner { override def start = VertxRunner.runServer(Vanilla.route(128)) }
