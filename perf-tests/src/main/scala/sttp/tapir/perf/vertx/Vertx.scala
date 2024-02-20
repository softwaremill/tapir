package sttp.tapir.perf.vertx

import _root_.cats.effect.IO
import _root_.cats.effect.kernel.Resource
import io.vertx.core.http.{HttpServerOptions, ServerWebSocket}
import io.vertx.core.streams.ReadStream
import io.vertx.core.{Future => VFuture, Handler, Vertx}
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis.{Endpoints, ServerRunner}
import sttp.tapir.server.vertx.streams.VertxStreams
import sttp.tapir.server.vertx.{VertxFutureServerInterpreter, VertxFutureServerOptions}

import scala.concurrent.Future

object Tapir extends Endpoints {
  import sttp.tapir._
  def route(nRoutes: Int, withServerLog: Boolean = false): Vertx => Router => Route = { vertx =>
    router =>
      val serverOptions = buildOptions(VertxFutureServerOptions.customiseInterceptors, withServerLog)
      val interpreter = VertxFutureServerInterpreter(serverOptions)
      val wsEndpoint = wsBaseEndpoint
        .out(
          webSocketBody[Long, CodecFormat.TextPlain, Long, CodecFormat.TextPlain](VertxStreams)
            .concatenateFragmentedFrames(false)
        )

      val laggedTimestampPipe: ReadStream[Long] => ReadStream[Long] = { inputStream =>
        new ReadStream[Long] {

          override def fetch(amount: Long): ReadStream[Long] = this

          private var dataHandler: Handler[Long] = _
          private var endHandler: Handler[Void] = _
          private var exceptionHandler: Handler[Throwable] = _

          inputStream.handler(new Handler[Long] {
            override def handle(event: Long): Unit = {
              vertx.setTimer(
                WebSocketSingleResponseLag.toMillis,
                _ => {
                  if (dataHandler != null) dataHandler.handle(System.currentTimeMillis())
                }
              ): Unit
            }
          })

          inputStream.endHandler(new Handler[Void] {
            override def handle(e: Void): Unit = {
              if (endHandler != null) endHandler.handle(e)
            }
          })

          inputStream.exceptionHandler(new Handler[Throwable] {
            override def handle(e: Throwable): Unit = {
              if (exceptionHandler != null) exceptionHandler.handle(e)
            }
          })

          override def handler(handler: Handler[Long]): ReadStream[Long] = {
            this.dataHandler = handler
            this
          }

          override def pause(): ReadStream[Long] = this
          override def resume(): ReadStream[Long] = this

          override def endHandler(endHandler: Handler[Void]): ReadStream[Long] = {
            this.endHandler = endHandler
            this
          }

          override def exceptionHandler(exceptionHandler: Handler[Throwable]): ReadStream[Long] = {
            this.exceptionHandler = exceptionHandler
            this
          }
        }

      }

      val wsServerEndpoint = wsEndpoint.serverLogicSuccess[Future] { _ =>
        Future.successful {
          laggedTimestampPipe
        }
      }
    (wsServerEndpoint :: genEndpointsFuture(nRoutes)).map(interpreter.route(_)(router)).last
  }
}
object Vanilla extends Endpoints {

  def bodyHandler = BodyHandler.create(false).setBodyLimit(LargeInputSize + 100L)
  def webSocketHandler(vertx: Vertx): Router => Route = { router =>
    router.get("/ws/ts").handler { ctx =>
      val wss = ctx.request().toWebSocket()
      wss.map {
        ws: ServerWebSocket =>
          ws.textMessageHandler(_ => ())

          // Set a periodic timer to send timestamps every 100 milliseconds
          val timerId = vertx.setPeriodic(
            WebSocketSingleResponseLag.toMillis,
            { _ =>
              ws.writeTextMessage(System.currentTimeMillis().toString): Unit
            }
          )

          // Close the timer when the WebSocket is closed
          ws.closeHandler(_ => vertx.cancelTimer(timerId): Unit)
      }: Unit
    }
  }
  def route: Int => Vertx => Router => Route = { (nRoutes: Int) => _ => router =>
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
  def runServer(route: Vertx => Router => Route, wsRoute: Option[Vertx => Router => Route] = None): IO[ServerRunner.KillSwitch] = {
    Resource
      .make(IO.delay(Vertx.vertx()))(vertx => IO.delay(vertx.close()).void)
      .flatMap { vertx =>
        val router = Router.router(vertx)
        val server = vertx.createHttpServer(new HttpServerOptions().setPort(Port)).requestHandler(router)
        val listenIO = vertxFutureToIo(server.listen(Port))
        wsRoute.foreach(r => r(vertx).apply(router))
        route(vertx).apply(router): Unit
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
object VanillaServer extends ServerRunner { override def start = VertxRunner.runServer(Vanilla.route(1), Some(Vanilla.webSocketHandler)) }
object VanillaMultiServer extends ServerRunner { override def start = VertxRunner.runServer(Vanilla.route(128)) }
