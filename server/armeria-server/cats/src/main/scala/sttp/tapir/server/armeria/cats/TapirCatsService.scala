package sttp.tapir.server.armeria.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import com.linecorp.armeria.common.{HttpData, HttpRequest, HttpResponse}
import com.linecorp.armeria.server.ServiceRequestContext
import fs2.interop.reactivestreams._
import fs2.{Chunk, Stream}
import java.util.concurrent.CompletableFuture
import org.reactivestreams.Publisher
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadAsyncError
import sttp.monad.syntax._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria._
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

private[cats] final case class TapirCatsService[F[_]: Async](
    serverEndpoints: List[ServerEndpoint[Fs2Streams[F], F]],
    armeriaServerOptions: ArmeriaCatsServerOptions[F]
) extends TapirService[Fs2Streams[F], F] {

  private[this] implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError()
  private[this] implicit val bodyListener: BodyListener[F, ArmeriaResponseType] = new ArmeriaBodyListener

  private[this] val dispatcher: Dispatcher[F] = armeriaServerOptions.dispatcher
  private[this] val fs2StreamCompatible: StreamCompatible[Fs2Streams[F]] = Fs2StreamCompatible(dispatcher)
  private[this] val interpreter: ServerInterpreter[Fs2Streams[F], F, ArmeriaResponseType, Fs2Streams[F]] =
    new ServerInterpreter(
      serverEndpoints,
      new ArmeriaToResponseBody(fs2StreamCompatible),
      armeriaServerOptions.interceptors,
      armeriaServerOptions.deleteFile
    )

  override def serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(ctx.eventLoop())
    implicit val catsFutureConversion: CatsFutureConversion[F] = new CatsFutureConversion(dispatcher)

    val serverRequest = new ArmeriaServerRequest(ctx)
    val requestBody = new ArmeriaRequestBody(ctx, armeriaServerOptions, fs2StreamCompatible)
    val future = new CompletableFuture[HttpResponse]()
    val result = interpreter(serverRequest, requestBody).map(ResultMapping.toArmeria)

    val (response, cancelRef) = dispatcher.unsafeToFutureCancelable(result)
    response.onComplete {
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
        cancelRef()
        ()
      }
    httpResponse
  }
}

private object Fs2StreamCompatible {
  def apply[F[_]: Async](dispatcher: Dispatcher[F]): StreamCompatible[Fs2Streams[F]] = {
    new StreamCompatible[Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]

      override def asStreamMessage(stream: Stream[F, Byte]): Publisher[HttpData] =
        StreamUnicastPublisher(
          stream.chunks
            .map { chunk =>
              val bytes = chunk.compact
              HttpData.wrap(bytes.values, bytes.offset, bytes.length)
            },
          dispatcher
        )

      override def fromArmeriaStream(publisher: Publisher[HttpData]): Stream[F, Byte] =
        publisher.toStreamBuffered[F](4).flatMap(httpData => Stream.chunk(Chunk.array(httpData.array())))
    }
  }
}

private class CatsFutureConversion[F[_]: Async](dispatcher: Dispatcher[F])(implicit ec: ExecutionContext) extends FutureConversion[F] {
  override def from[A](f: => Future[A]): F[A] = {
    Async[F].async_ { cb =>
      f.onComplete {
        case Failure(exception) => cb(Left(exception))
        case Success(value)     => cb(Right(value))
      }
      ()
    }
  }

  override def to[A](f: => F[A]): Future[A] = dispatcher.unsafeToFuture(f)
}
