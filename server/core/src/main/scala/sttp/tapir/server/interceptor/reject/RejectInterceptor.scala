package sttp.tapir.server.interceptor.reject

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor._

/** Specifies what should be done if decoding the request has failed for all endpoints, and multiple endpoints have been interpreted
  * (doesn't do anything when interpreting a single endpoint).
  *
  * By default, if there's a method decode failure, this means that the path must have matched (as it's decoded first); then, returning a
  * 405 (method not allowed).
  *
  * In other cases, not returning a response, assuming that the interpreter will return a "no match" to the server implementation.
  */
class RejectInterceptor[F[_]](handler: RejectHandler[F]) extends RequestInterceptor[F] {
  override def apply[R, B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
  ): RequestHandler[F, R, B] = {
    val next = requestHandler(EndpointInterceptor.noop)
    new RequestHandler[F, R, B] {
      override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
          monad: MonadError[F]
      ): F[RequestResult[B]] =
        next(request, endpoints).flatMap {
          case r: RequestResult.Response[B] => (r: RequestResult[B]).unit
          case f: RequestResult.Failure =>
            handler(RejectContext(f, request)).flatMap {
              case Some(value) => responder(request, value).map(RequestResult.Response(_))
              case None        => (f: RequestResult[B]).unit
            }
        }
    }
  }
}

object RejectInterceptor {

  /** When interpreting a single endpoint, disabling the reject interceptor, as returning a method mismatch only makes sense when there are
    * more endpoints
    */
  def disableWhenSingleEndpoint[F[_]](interceptors: List[Interceptor[F]], ses: List[ServerEndpoint[_, F]]): List[Interceptor[F]] =
    if (ses.length > 1) interceptors else interceptors.filterNot(_.isInstanceOf[RejectInterceptor[F]])
}
