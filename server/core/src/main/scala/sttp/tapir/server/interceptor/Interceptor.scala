package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.{ServerResponse, ValuedEndpointOutput}

/** Intercepts requests, and endpoint decode events. Using interceptors it's possible to:
  *
  *   - customise the request that is passed downstream
  *   - short-circuit further processing and provide an alternate (or no) response
  *   - replace or modify the response that is sent back to the client
  *
  * Interceptors can be called when the request is started to be processed (use [[RequestInterceptor]] in this case), or for each endpoint,
  * with either input success of failure decoding events (see [[EndpointInterceptor]]).
  *
  * To add an interceptors, modify the server options of the server interpreter.
  *
  * @tparam F
  *   The effect type constructor.
  */
sealed trait Interceptor[F[_]]

/** Allows intercepting the handling of `request`, before decoding using any of the endpoints is done. The request can be modified, before
  * invoking further behavior, passed through `requestHandler`. Ultimately, when all interceptors are run, logic decoding subsequent
  * endpoint inputs will be run.
  *
  * A request interceptor is called once for a request.
  *
  * Instead of calling the nested behavior, alternative responses can be returned using the `responder`.
  *
  * Moreover, when calling `requestHandler`, an [[EndpointInterceptor]] can be provided, which will be added to the list of endpoint
  * interceptors to call. The order in which the endpoint interceptors will be called will correspond to their order in the interceptors
  * list in the server options. An "empty" interceptor can be provided using [[EndpointInterceptor.noop]].
  *
  * @tparam F
  *   The effect type constructor.
  */
trait RequestInterceptor[F[_]] extends Interceptor[F] {

  /** @tparam R
    *   The interpreter-specific supported capabilities, such as streaming support, websockets or `Any`.
    * @tparam B
    *   The interpreter-specific, low-level type of body.
    */
  def apply[R, B](responder: Responder[F, B], requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]): RequestHandler[F, R, B]
}

object RequestInterceptor {

  /** Create a request interceptor which transforms the server request, prior to handling any endpoints. */
  def transformServerRequest[F[_]](f: ServerRequest => F[ServerRequest]): RequestInterceptor[F] = new RequestInterceptor[F] {
    override def apply[R, B](
        responder: Responder[F, B],
        requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
    ): RequestHandler[F, R, B] =
      new RequestHandler[F, R, B] {
        override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
            monad: MonadError[F]
        ): F[RequestResult[B]] =
          f(request).flatMap(request2 => requestHandler(EndpointInterceptor.noop)(request2, endpoints))
      }
  }

  trait RequestResultTransform[F[_]] {
    def apply[B](request: ServerRequest, result: RequestResult[B]): F[RequestResult[B]]
  }

  /** Create a request interceptor which transforms the result, which might be either a response, or a list of endpoint decoding failures.
    */
  def transformResult[F[_]](f: RequestResultTransform[F]): RequestInterceptor[F] = new RequestInterceptor[F] {
    override def apply[R, B](
        responder: Responder[F, B],
        requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
    ): RequestHandler[F, R, B] =
      new RequestHandler[F, R, B] {
        override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
            monad: MonadError[F]
        ): F[RequestResult[B]] =
          requestHandler(EndpointInterceptor.noop)(request, endpoints).flatMap(f(request, _))
      }
  }

  trait RequestResultEffectTransform[F[_]] {
    def apply[B](request: ServerRequest, result: F[RequestResult[B]]): F[RequestResult[B]]
  }

  /** Create a request interceptor which transforms the *effect* which computes the result (either a response, or a list of endpoint
    * decoding failures), that is the `F[RequestResult[B]]` value. To transform the result itself, it might be easier to use
    * [[transformResult]].
    */
  def transformResultEffect[F[_]](f: RequestResultEffectTransform[F]): RequestInterceptor[F] = new RequestInterceptor[F] {
    override def apply[R, B](
        responder: Responder[F, B],
        requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
    ): RequestHandler[F, R, B] =
      new RequestHandler[F, R, B] {
        override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
            monad: MonadError[F]
        ): F[RequestResult[B]] =
          f(request, requestHandler(EndpointInterceptor.noop)(request, endpoints))
      }
  }

  trait ServerEndpointFilter[F[_]] {
    def apply[R](endpoints: List[ServerEndpoint[R, F]]): F[List[ServerEndpoint[R, F]]]
  }

  /** Filter the server endpoints for which decoding will be later attempted, in sequence. */
  def filterServerEndpoints[F[_]](filter: ServerEndpointFilter[F]): RequestInterceptor[F] =
    new RequestInterceptor[F] {
      override def apply[R, B](
          responder: Responder[F, B],
          requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
      ): RequestHandler[F, R, B] = {
        new RequestHandler[F, R, B] {
          override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
              monad: MonadError[F]
          ): F[RequestResult[B]] = {
            filter(endpoints).flatMap(endpoints2 => requestHandler(EndpointInterceptor.noop)(request, endpoints2))
          }
        }
      }
    }

  /** Run an effect when a request is received. */
  def effect[F[_]](f: ServerRequest => F[Unit]): RequestInterceptor[F] = new RequestInterceptor[F] {
    override def apply[R, B](
        responder: Responder[F, B],
        requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
    ): RequestHandler[F, R, B] =
      new RequestHandler[F, R, B] {
        override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
            monad: MonadError[F]
        ): F[RequestResult[B]] =
          f(request).flatMap(_ => requestHandler(EndpointInterceptor.noop)(request, endpoints))
      }
  }
}

/** Allows intercepting the handling of a request by an endpoint, when either the endpoint's inputs have been decoded successfully, or when
  * decoding has failed. Ultimately, when all interceptors are run, the endpoint's server logic will be run (in case of a decode success),
  * or `None` will be returned (in case of decode failure).
  *
  * Instead of calling the nested behavior, alternative responses can be returned using the `responder`.
  */
trait EndpointInterceptor[F[_]] extends Interceptor[F] {

  /** @tparam B The interpreter-specific, low-level type of body. */
  def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B]
}

object EndpointInterceptor {
  def noop[F[_]]: EndpointInterceptor[F] = new EndpointInterceptor[F] {
    override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] = endpointHandler
  }
}

trait Responder[F[_], B] {
  def apply[O](request: ServerRequest, output: ValuedEndpointOutput[O]): F[ServerResponse[B]]
}
