package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.EntityStreamSizeException
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.model.ValuedEndpointOutput

import scala.concurrent.Future

private[akkahttp] object AkkaStreamSizeExceptionInterceptor
    extends ExceptionInterceptor[Future](new ExceptionHandler[Future] {
      override def apply(ctx: ExceptionContext)(implicit monad: MonadError[Future]): Future[Option[ValuedEndpointOutput[_]]] = {
        ctx.e match {
          case ex: Exception if ex.getCause().isInstanceOf[EntityStreamSizeException] =>
            monad.error(StreamMaxLengthExceededException(ex.getCause().asInstanceOf[EntityStreamSizeException].limit))
          case EntityStreamSizeException(limit, _) =>
            monad.error(StreamMaxLengthExceededException(limit))
          case other =>
            monad.error(other)
        }
      }
    })
