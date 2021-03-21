package sttp.tapir.server.armeria.zio

import _root_.zio._
import _root_.zio.interop.reactivestreams._
import _root_.zio.stream.Stream
import com.linecorp.armeria.common.{HttpData, HttpRequest, HttpResponse}
import com.linecorp.armeria.server.ServiceRequestContext
import java.util.concurrent.CompletableFuture
import org.reactivestreams.Publisher
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria._
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.ztapir.RIOMonadError

private[zio] final case class TapirZioService[R](
    serverEndpoints: List[ServerEndpoint[ZioStreams, RIO[R, *]]],
    armeriaServerOptions: ArmeriaZioServerOptions[RIO[R, *]]
)(implicit runtime: Runtime[R])
    extends TapirService[ZioStreams, RIO[R, *]] {

  private[this] implicit val monad: RIOMonadError[R] = new RIOMonadError()
  private[this] implicit val bodyListener: ArmeriaBodyListener[RIO[R, *]] = new ArmeriaBodyListener
  private[this] val zioStreamCompatible: StreamCompatible[ZioStreams] = ZioStreamCompatible(runtime)

  override def serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(ctx.eventLoop())

    val rioFromFuture = new RioFromFuture[R]
    val serverRequest = new ArmeriaServerRequest(ctx)
    val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], ArmeriaResponseType, ZioStreams](
      serverEndpoints,
      new ArmeriaToResponseBody(zioStreamCompatible),
      armeriaServerOptions.interceptors,
      file => rioFromFuture(armeriaServerOptions.deleteFile(ctx, file))
    )

    val requestBody = new ArmeriaRequestBody(ctx, armeriaServerOptions, rioFromFuture, zioStreamCompatible)
    val future = new CompletableFuture[HttpResponse]()
    val result = interpreter(serverRequest, requestBody).map(ResultMapping.toArmeria)
    val cancellable = runtime.unsafeRunToFuture(result)
    cancellable.future.onComplete {
      case Failure(exception) =>
        future.completeExceptionally(exception)
      case Success(value) =>
        future.complete(value)
    }

    val httpResponse = HttpResponse.from(future)
    httpResponse
      .whenComplete()
      .asInstanceOf[CompletableFuture[Unit]]
      .exceptionally { case (_: Throwable) =>
        cancellable.cancel()
        ()
      }
    httpResponse
  }
}

private object ZioStreamCompatible {
  def apply(runtime: Runtime[Any]): StreamCompatible[ZioStreams] = {
    new StreamCompatible[ZioStreams] {
      override val streams: ZioStreams = ZioStreams

      override def asStreamMessage(stream: Stream[Throwable, Byte]): Publisher[HttpData] =
        runtime.unsafeRun(stream.mapChunks(c => Chunk.single(HttpData.wrap(c.toArray))).toPublisher)

      override def fromArmeriaStream(publisher: Publisher[HttpData]): Stream[Throwable, Byte] =
        publisher.toStream().mapConcatChunk(httpData => Chunk.fromArray(httpData.array()))
    }
  }
}

private class RioFromFuture[R](implicit ec: ExecutionContext) extends FromFuture[RIO[R, *]] {
  def apply[T](f: => Future[T]): RIO[R, T] = {
    RIO.effectAsync { cb =>
      f.onComplete {
        case Failure(exception) => cb(Task.fail(exception))
        case Success(value)     => cb(Task.succeed(value))
      }
    }
  }
}
