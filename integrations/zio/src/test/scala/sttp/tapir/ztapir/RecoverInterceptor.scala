package sttp.tapir.ztapir

import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor._
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.ztapir.ZTapirTest.ResponseBodyType
import zio.Task

case class RecoverInterceptor[T](recoverF: Throwable => ValuedEndpointOutput[T]) extends RequestInterceptor[Task] {

  override def apply[R, B](
      responder: Responder[Task, B],
      requestHandler: EndpointInterceptor[Task] => RequestHandler[Task, R, B]
  ): RequestHandler[Task, R, B] = {

    val next = requestHandler(EndpointInterceptor.noop)
    new RequestHandler[Task, R, B] {
      override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, Task]])(implicit
          monad: MonadError[Task]
      ): Task[RequestResult[B]] = {
        next(request, endpoints).catchNonFatalOrDie(e => responder(request, recoverF(e)).map(RequestResult.Response(_)))
      }
    }
  }
}
