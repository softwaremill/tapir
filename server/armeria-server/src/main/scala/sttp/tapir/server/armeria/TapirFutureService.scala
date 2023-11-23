package sttp.tapir.server.armeria

import com.linecorp.armeria.common.{HttpData, HttpRequest, HttpResponse}
import com.linecorp.armeria.server.ServiceRequestContext

import java.util.concurrent.CompletableFuture
import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.monad.FutureMonad
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}

private[armeria] final case class TapirFutureService(
    serverEndpoints: List[ServerEndpoint[ArmeriaStreams, Future]],
    armeriaServerOptions: ArmeriaFutureServerOptions
) extends TapirService[ArmeriaStreams, Future] {

  private implicit val futureConversion: FutureConversion[Future] = FutureConversion.identity

  override def serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(ctx.eventLoop())
    implicit val monad: FutureMonad = new FutureMonad()
    implicit val bodyListener: BodyListener[Future, ArmeriaResponseType] = new ArmeriaBodyListener

    val serverRequest = new ArmeriaServerRequest(ctx)
    val future = new CompletableFuture[HttpResponse]()
    val interpreter: ServerInterpreter[ArmeriaStreams, Future, ArmeriaResponseType, ArmeriaStreams] = new ServerInterpreter(
      FilterServerEndpoints(serverEndpoints),
      new ArmeriaRequestBody(armeriaServerOptions, ArmeriaStreamCompatible),
      new ArmeriaToResponseBody(ArmeriaStreamCompatible),
      RejectInterceptor.disableWhenSingleEndpoint(armeriaServerOptions.interceptors, serverEndpoints),
      armeriaServerOptions.deleteFile
    )

    interpreter(serverRequest)
      .map(ResultMapping.toArmeria)
      .onComplete {
        case Failure(exception) =>
          future.completeExceptionally(exception)
        case Success(value) =>
          future.complete(value)
      }
    HttpResponse.from(future)
  }
}

private object ArmeriaStreamCompatible extends StreamCompatible[ArmeriaStreams] {
  override val streams: ArmeriaStreams = ArmeriaStreams

  override def fromArmeriaStream(s: Publisher[HttpData], maxBytes: Option[Long]): Publisher[HttpData] = s

  override def asStreamMessage(s: Publisher[HttpData]): Publisher[HttpData] = s
}
