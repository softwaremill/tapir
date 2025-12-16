package sttp.tapir.server.stub

import sttp.client3.{Request, Response}
import sttp.model.RequestMetadata
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{Interceptor, RequestResult}
import sttp.tapir.server.interpreter._
import sttp.tapir.server.model.ServerResponse

import scala.util.{Success, Try}

private[stub] object StubServerInterpreter {
  def apply[F[_]: MonadError, R, T](
      req: Request[T, R],
      endpoints: List[ServerEndpoint[R, F]],
      interceptors: List[Interceptor[F]]
  ): F[Response[_]] = {

    implicit val bodyListener: BodyListener[F, Any] = new BodyListener[F, Any] {
      override def onComplete(body: Any)(cb: Try[Unit] => F[Unit]): F[Any] = cb(Success(())).map(_ => body)
    }

    val interpreter =
      new ServerInterpreter[R, F, Any, AnyStreams](
        FilterServerEndpoints(endpoints),
        new SttpRequestBody[F],
        SttpResponseEncoder.toResponseBody,
        interceptors,
        _ => ().unit
      )

    val sRequest = new SttpRequest(req)

    interpreter.apply(sRequest).map {
      case RequestResult.Response(sResponse, _) => toResponse(sRequest, sResponse)
      case RequestResult.Failure(_)             => toResponse(sRequest, ServerResponse.notFound)
    }
  }

  private val toResponse: (ServerRequest, ServerResponse[Any]) => Response[Any] = (sRequest, sResponse) => {
    val metadata = RequestMetadata(sRequest.method, sRequest.uri, sRequest.headers)
    sttp.client3.Response(sResponse.body.getOrElse(()), sResponse.code, "", sResponse.headers, Nil, metadata)
  }
}
