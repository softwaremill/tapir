package sttp.tapir.server.armeria.zio

import _root_.zio._
import _root_.zio.interop.reactivestreams._
import _root_.zio.stream.Stream
import com.linecorp.armeria.common.{HttpData, HttpRequest, HttpResponse}
import com.linecorp.armeria.server.ServiceRequestContext
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria._
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{FilterServerEndpoints, ServerInterpreter}

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[zio] final case class TapirZioService[R](
    serverEndpoints: List[ServerEndpoint[ZioStreams, RIO[R, *]]],
    armeriaServerOptions: ArmeriaZioServerOptions[RIO[R, *]]
)(implicit runtime: Runtime[R])
    extends TapirService[ZioStreams, RIO[R, *]] {

  private[this] implicit val monad: RIOMonadAsyncError[R] = new RIOMonadAsyncError()
  private[this] implicit val bodyListener: ArmeriaBodyListener[RIO[R, *]] = new ArmeriaBodyListener

  private[this] val zioStreamCompatible: StreamCompatible[ZioStreams] = ZioStreamCompatible(runtime)

  override def serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(ctx.eventLoop())
    implicit val rioFutureConversion: RioFutureConversion[R] = new RioFutureConversion[R]

    val interpreter: ServerInterpreter[ZioStreams, RIO[R, *], ArmeriaResponseType, ZioStreams] =
      new ServerInterpreter[ZioStreams, RIO[R, *], ArmeriaResponseType, ZioStreams](
        FilterServerEndpoints(serverEndpoints),
        new ArmeriaRequestBody(armeriaServerOptions, zioStreamCompatible),
        new ArmeriaToResponseBody(zioStreamCompatible),
        RejectInterceptor.disableWhenSingleEndpoint(armeriaServerOptions.interceptors, serverEndpoints),
        armeriaServerOptions.deleteFile
      )

    val serverRequest = ArmeriaServerRequest(ctx)
    val future = new CompletableFuture[HttpResponse]()
    val result = interpreter(serverRequest).map(ResultMapping.toArmeria)

    val cancellable = Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(result))
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
        Unsafe.unsafe(implicit u =>
          runtime.unsafe
            .run(stream.mapChunks(c => Chunk.single(HttpData.wrap(c.toArray))).toPublisher)
            .getOrThrowFiberFailure()
        )

      override def fromArmeriaStream(publisher: Publisher[HttpData], maxBytes: Option[Long]): Stream[Throwable, Byte] = {
        val stream = publisher.toZIOStream().mapConcatChunk(httpData => Chunk.fromArray(httpData.array()))
        maxBytes.map(ZioStreams.limitBytes(stream, _)).getOrElse(stream)
      }
    }
  }
}

private class RioFutureConversion[R](implicit ec: ExecutionContext, runtime: Runtime[R]) extends FutureConversion[RIO[R, *]] {
  def from[T](f: => Future[T]): RIO[R, T] = {
    ZIO.async { cb =>
      f.onComplete {
        case Failure(exception) => cb(ZIO.fail(exception))
        case Success(value)     => cb(ZIO.succeed(value))
      }
    }
  }

  override def to[A](f: => RIO[R, A]): Future[A] =
    Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(f))
}
