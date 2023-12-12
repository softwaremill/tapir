package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model.EntityStreamSizeException
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.exception.{ExceptionContext, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.model.ValuedEndpointOutput

import scala.concurrent.Future

/** Used by PekkoHttpServerInterpreter to catch specific scenarios related to exceeding max content length in requests:
  *   - EntityStreamSizeException thrown when InputBody is a Pekko Stream, which exceeds max length limit during processing in serverLogic.
  *   - A wrapped EntityStreamSizeException failure, a variant of previous scenario where additional stage (like FileIO sink) wraps the
  *     underlying cause into another exception.
  *   - An InputStreamBody throws an IOException(EntityStreamSizeException) when reading the input stream fails due to exceeding max length
  *     limit in the underlying Pekko Stream.
  *
  * All these scenarios mean basically the same, so we'll fail with our own StreamMaxLengthExceededException, a general mechanism intended
  * to be handled by Tapir and result in a HTTP 413 Payload Too Large response.
  */
private[pekkohttp] object PekkoStreamSizeExceptionInterceptor
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
