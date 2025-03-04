package sttp.tapir.client.sttp4.ws

import sttp.client4.{WebSocketBackend, WebSocketRequest}
import sttp.model.Uri
import sttp.tapir.client.sttp4.{SttpClientInterpreter, SttpClientOptions, WebSocketToPipe}
import sttp.tapir.{DecodeResult, Endpoint, PublicEndpoint}
import sttp.capabilities.{Streams, WebSockets}

trait WebSocketSttpClientInterpreter extends SttpClientInterpreter {

  def sttpClientOptions: SttpClientOptions = SttpClientOptions.default

  // public

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result of decoding the response (error
    * or success value) is returned.
    */
  def toClient[F[_], I, E, O, R <: Streams[_] with WebSockets](
      e: PublicEndpoint[I, E, O, R],
      baseUri: Option[Uri],
      backend: WebSocketBackend[F]
  )(implicit wsToPipe: WebSocketToPipe[R]): I => F[DecodeResult[Either[E, O]]] = { (i: I) =>
    val req = toRequest[F, I, E, O, R](e, baseUri)
    backend.monad.map(req(i).send(backend))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result (error or success value) is
    * returned. If decoding the result fails, a failed effect is returned instead.
    */
  def toClientThrowDecodeFailures[F[_], I, E, O, R <: Streams[_] with WebSockets](
      e: PublicEndpoint[I, E, O, R],
      baseUri: Option[Uri],
      backend: WebSocketBackend[F]
  )(implicit wsToPipe: WebSocketToPipe[R]): I => F[Either[E, O]] = {
    val req = toRequestThrowDecodeFailures[F, I, E, O, R](e, baseUri)
    (i: I) => backend.monad.map(req(i).send(backend))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result (success value) is returned. If
    * decoding the result fails, or if the response corresponds to an error value, a failed effect is returned instead.
    */
  def toClientThrowErrors[F[_], I, E, O, R <: Streams[_] with WebSockets](
      e: PublicEndpoint[I, E, O, R],
      baseUri: Option[Uri],
      backend: WebSocketBackend[F]
  )(implicit wsToPipe: WebSocketToPipe[R]): I => F[O] = {
    val req = toRequestThrowErrors[F, I, E, O, R](e, baseUri)
    (i: I) => backend.monad.map(req(i).send(backend))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The result of the function is a description of a request, which can be sent using any sttp
    * backend. The response will then contain the decoded error or success values (note that this can be the body enriched with data from
    * headers/status code).
    */
  def toRequest[F[_], I, E, O, R <: Streams[_] with WebSockets](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => WebSocketRequest[F, DecodeResult[Either[E, O]]] = {
    new WebSocketEndpointToSttpClient(wsToPipe).toSttpRequest(e, baseUri).apply(())
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The result of the function is a description of a request, which can be sent using any sttp
    * backend. The response will then contain the decoded error or success values (note that this can be the body enriched with data from
    * headers/status code), or will be a failed effect, when response parsing fails.
    */
  def toRequestThrowDecodeFailures[F[_], I, E, O, R <: Streams[_] with WebSockets](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri])(
      implicit wsToPipe: WebSocketToPipe[R]
  ): I => WebSocketRequest[F, Either[E, O]] =
    i => new WebSocketEndpointToSttpClient(wsToPipe).toSttpRequest(e, baseUri).apply(()).apply(i).mapResponse(throwDecodeFailures)

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The result of the function is a description of a request, which can be sent using any sttp
    * backend. The response will then contain the decoded success values (note that this can be the body enriched with data from
    * headers/status code), or will be a failed effect, when response parsing fails or if the result is an error.
    *
    * @throws IllegalArgumentException
    *   when response parsing fails
    */
  def toRequestThrowErrors[F[_], I, E, O, R <: Streams[_] with WebSockets](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => WebSocketRequest[F, O] =
    i =>
      new WebSocketEndpointToSttpClient(wsToPipe)
        .toSttpRequest(e, baseUri)
        .apply(())
        .apply(i)
        .mapResponse(throwDecodeFailures)
        .mapResponse {
          case Left(t: Throwable) => throw new RuntimeException(throwErrorExceptionMsg(e, i, t.asInstanceOf[E]), t)
          case Left(t)            => throw new RuntimeException(throwErrorExceptionMsg(e, i, t))
          case Right(o)           => o
        }

  // secure

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using the given backend, and the result of decoding
    * the response (error or success value) is returned.
    */
  def toSecureClient[F[_], A, I, E, O, R <: Streams[_] with WebSockets](
      e: Endpoint[A, I, E, O, R],
      baseUri: Option[Uri],
      backend: WebSocketBackend[F]
  )(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => F[DecodeResult[Either[E, O]]] = {
    val req = toSecureRequest[F, A, I, E, O, R](e, baseUri)
    (a: A) => (i: I) => backend.monad.map(req(a)(i).send(backend))(_.body)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using the given backend, and the result (error or
    * success value) is returned. If decoding the result fails, a failed effect is returned instead.
    */
  def toSecureClientThrowDecodeFailures[F[_], A, I, E, O, R <: Streams[_] with WebSockets](
      e: Endpoint[A, I, E, O, R],
      baseUri: Option[Uri],
      backend: WebSocketBackend[F]
  )(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => F[Either[E, O]] = {
    val req = toSecureRequestThrowDecodeFailures[F, A, I, E, O, R](e, baseUri)
    (a: A) => (i: I) => backend.monad.map(req(a)(i).send(backend))(_.body)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using the given backend, and the result (success
    * value) is returned. If decoding the result fails, or if the response corresponds to an error value, a failed effect is returned
    * instead.
    */
  def toSecureClientThrowErrors[F[_], A, I, E, O, R <: Streams[_] with WebSockets](
      e: Endpoint[A, I, E, O, R],
      baseUri: Option[Uri],
      backend: WebSocketBackend[F]
  )(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => F[O] = {
    val req = toSecureRequestThrowErrors[F, A, I, E, O, R](e, baseUri)
    (a: A) => (i: I) => backend.monad.map(req(a)(i).send(backend))(_.body)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The result of the function is a description of a request, which can be
    * sent using any sttp backend. The response will then contain the decoded error or success values (note that this can be the body
    * enriched with data from headers/status code).
    */
  def toSecureRequest[F[_], A, I, E, O, R <: Streams[_] with WebSockets](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => WebSocketRequest[F, DecodeResult[Either[E, O]]] =
    new WebSocketEndpointToSttpClient(wsToPipe).toSttpRequest(e, baseUri)

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The result of the function is a description of a request, which can be
    * sent using any sttp backend. The response will then contain the decoded error or success values (note that this can be the body
    * enriched with data from headers/status code), or will be a failed effect, when response parsing fails.
    */
  def toSecureRequestThrowDecodeFailures[F[_], A, I, E, O, R <: Streams[_] with WebSockets](
      e: Endpoint[A, I, E, O, R],
      baseUri: Option[Uri]
  )(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => WebSocketRequest[F, Either[E, O]] =
    a => i => new WebSocketEndpointToSttpClient(wsToPipe).toSttpRequest(e, baseUri).apply(a).apply(i).mapResponse(throwDecodeFailures)

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The result of the function is a description of a request, which can be
    * sent using any sttp backend. The response will then contain the decoded success values (note that this can be the body enriched with
    * data from headers/status code), or will be a failed effect, when response parsing fails or if the result is an error.
    *
    * @throws IllegalArgumentException
    *   when response parsing fails
    */
  def toSecureRequestThrowErrors[F[_], A, I, E, O, R <: Streams[_] with WebSockets](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri])(
      implicit wsToPipe: WebSocketToPipe[R]
  ): A => I => WebSocketRequest[F, O] =
    a =>
      i =>
        new WebSocketEndpointToSttpClient(wsToPipe)
          .toSttpRequest(e, baseUri)
          .apply(a)
          .apply(i)
          .mapResponse(throwDecodeFailures)
          .mapResponse {
            case Left(t: Throwable) => throw new RuntimeException(throwErrorExceptionMsg(e, a, i, t.asInstanceOf[E]), t)
            case Left(t)            => throw new RuntimeException(throwErrorExceptionMsg(e, a, i, t))
            case Right(o)           => o
          }
}

object WebSocketSttpClientInterpreter {
  def apply(clientOptions: SttpClientOptions = SttpClientOptions.default): WebSocketSttpClientInterpreter = {
    new WebSocketSttpClientInterpreter {
      override def sttpClientOptions: SttpClientOptions = clientOptions
    }
  }
}
