package sttp.tapir.server.interceptor.reject

import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.{
  EndpointInterceptor,
  RequestHandler,
  RequestInterceptor,
  RequestResult,
  Responder,
  ValuedEndpointOutput
}

/** Specifies what should be done if decoding the request has failed for all endpoints, and multiple endpoints have been interpreted
  * (doesn't do anything when interpreting a single endpoint).
  *
  * By default, if there's a method decode failure, this means that the path must have matched (as it's decoded first); then, returning a
  * 405 (method not allowed).
  *
  * In other cases, not returning a response, assuming that the interpreter will return a "no match" to the server implementation.
  */
class RejectInterceptor[F[_]](handler: RequestResult.Failure => Option[StatusCode]) extends RequestInterceptor[F] {
  override def apply[B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, B]
  ): RequestHandler[F, B] = {
    val next = requestHandler(EndpointInterceptor.noop)
    new RequestHandler[F, B] {
      override def apply(request: ServerRequest)(implicit monad: MonadError[F]): F[RequestResult[B]] =
        next(request).flatMap {
          case r: RequestResult.Response[B] => (r: RequestResult[B]).unit
          // when interpreting a single endpoint (hence dealing with a single failure), not doing anything, as returning
          // a method mismatch only makes sense when there are more endpoints
          case f: RequestResult.Failure if f.failures.size <= 1 => (f: RequestResult[B]).unit
          case f: RequestResult.Failure =>
            handler(f) match {
              case Some(sc) => responder(request, ValuedEndpointOutput(statusCode, sc)).map(RequestResult.Response(_))
              case None     => (f: RequestResult[B]).unit
            }
        }
    }
  }
}

object RejectInterceptor {
  def default[F[_]] = new RejectInterceptor[F](failure => {
    if (hasMethodMismatch(failure)) Some(StatusCode.MethodNotAllowed) else None
  })

  def defaultOrNotFound[F[_]] = new RejectInterceptor[F](failure => {
    if (hasMethodMismatch(failure)) Some(StatusCode.MethodNotAllowed) else Some(StatusCode.NotFound)
  })

  def hasMethodMismatch(f: RequestResult.Failure): Boolean = f.failures.map(_.failingInput).exists {
    case _: EndpointInput.FixedMethod[_] => true
    case _                               => false
  }
}
