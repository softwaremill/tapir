package sttp.tapir.client.sttp

import sttp.client3.{Request, SttpBackend}
import sttp.model.Uri
import sttp.tapir.{DecodeResult, Endpoint, PublicEndpoint}

trait SttpClientInterpreter extends SttpClientInterpreterExtensions {

  def sttpClientOptions: SttpClientOptions = SttpClientOptions.default

  // public

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result of decoding the response (error
    * or success value) is returned.
    */
  def toClient[F[_], I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri], backend: SttpBackend[F, R])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => F[DecodeResult[Either[E, O]]] = {
    val req = toRequest(e, baseUri)
    (i: I) => backend.responseMonad.map(backend.send(req(i)))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result (error or success value) is
    * returned. If decoding the result fails, a failed effect is returned instead.
    */
  def toClientThrowDecodeFailures[F[_], I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri], backend: SttpBackend[F, R])(
      implicit wsToPipe: WebSocketToPipe[R]
  ): I => F[Either[E, O]] = {
    val req = toRequestThrowDecodeFailures(e, baseUri)
    (i: I) => backend.responseMonad.map(backend.send(req(i)))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result (success value) is returned. If
    * decoding the result fails, or if the response corresponds to an error value, a failed effect is returned instead.
    */
  def toClientThrowErrors[F[_], I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri], backend: SttpBackend[F, R])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => F[O] = {
    val req = toRequestThrowErrors(e, baseUri)
    (i: I) => backend.responseMonad.map(backend.send(req(i)))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The result of the function is a description of a request, which can be sent using any sttp
    * backend. The response will then contain the decoded error or success values (note that this can be the body enriched with data from
    * headers/status code).
    */
  def toRequest[I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => Request[DecodeResult[Either[E, O]], R] =
    new EndpointToSttpClient(sttpClientOptions, wsToPipe).toSttpRequest(e, baseUri).apply(())

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The result of the function is a description of a request, which can be sent using any sttp
    * backend. The response will then contain the decoded error or success values (note that this can be the body enriched with data from
    * headers/status code), or will be a failed effect, when response parsing fails.
    */
  def toRequestThrowDecodeFailures[I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => Request[Either[E, O], R] =
    i => new EndpointToSttpClient(sttpClientOptions, wsToPipe).toSttpRequest(e, baseUri).apply(()).apply(i).mapResponse(throwDecodeFailures)

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
  def toRequestThrowErrors[I, E, O, R](e: PublicEndpoint[I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): I => Request[O, R] =
    i => {
      val request = new EndpointToSttpClient(sttpClientOptions, wsToPipe).toSttpRequest(e, baseUri).apply(()).apply(i)
      request
        .mapResponse(throwDecodeFailures)
        .mapResponse {
          case Left(t: Throwable) => throw new RuntimeException(throwErrorExceptionMsg(e, i, t.asInstanceOf[E], request), t)
          case Left(t)            => throw new RuntimeException(throwErrorExceptionMsg(e, i, t, request))
          case Right(o)           => o
        }
    }

  // secure

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using the given backend, and the result of decoding
    * the response (error or success value) is returned.
    */
  def toSecureClient[F[_], A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri], backend: SttpBackend[F, R])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => F[DecodeResult[Either[E, O]]] = {
    val req = toSecureRequest(e, baseUri)
    (a: A) => (i: I) => backend.responseMonad.map(backend.send(req(a)(i)))(_.body)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using the given backend, and the result (error or
    * success value) is returned. If decoding the result fails, a failed effect is returned instead.
    */
  def toSecureClientThrowDecodeFailures[F[_], A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri], backend: SttpBackend[F, R])(
      implicit wsToPipe: WebSocketToPipe[R]
  ): A => I => F[Either[E, O]] = {
    val req = toSecureRequestThrowDecodeFailures(e, baseUri)
    (a: A) => (i: I) => backend.responseMonad.map(backend.send(req(a)(i)))(_.body)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using the given backend, and the result (success
    * value) is returned. If decoding the result fails, or if the response corresponds to an error value, a failed effect is returned
    * instead.
    */
  def toSecureClientThrowErrors[F[_], A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri], backend: SttpBackend[F, R])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => F[O] = {
    val req = toSecureRequestThrowErrors(e, baseUri)
    (a: A) => (i: I) => backend.responseMonad.map(backend.send(req(a)(i)))(_.body)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The result of the function is a description of a request, which can be
    * sent using any sttp backend. The response will then contain the decoded error or success values (note that this can be the body
    * enriched with data from headers/status code).
    */
  def toSecureRequest[A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => Request[DecodeResult[Either[E, O]], R] =
    new EndpointToSttpClient(sttpClientOptions, wsToPipe).toSttpRequest(e, baseUri)

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The result of the function is a description of a request, which can be
    * sent using any sttp backend. The response will then contain the decoded error or success values (note that this can be the body
    * enriched with data from headers/status code), or will be a failed effect, when response parsing fails.
    */
  def toSecureRequestThrowDecodeFailures[A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => Request[Either[E, O], R] =
    a =>
      i =>
        new EndpointToSttpClient(sttpClientOptions, wsToPipe).toSttpRequest(e, baseUri).apply(a).apply(i).mapResponse(throwDecodeFailures)

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
  def toSecureRequestThrowErrors[A, I, E, O, R](e: Endpoint[A, I, E, O, R], baseUri: Option[Uri])(implicit
      wsToPipe: WebSocketToPipe[R]
  ): A => I => Request[O, R] =
    a =>
      i => {
        val request = new EndpointToSttpClient(sttpClientOptions, wsToPipe).toSttpRequest(e, baseUri).apply(a).apply(i)
        request
          .mapResponse(throwDecodeFailures)
          .mapResponse {
            case Left(t: Throwable) => throw new RuntimeException(throwErrorExceptionMsg(e, a, i, t.asInstanceOf[E], request), t)
            case Left(t)            => throw new RuntimeException(throwErrorExceptionMsg(e, a, i, t, request))
            case Right(o)           => o
          }
      }

  //

  private def throwDecodeFailures[T](dr: DecodeResult[T]): T =
    dr match {
      case DecodeResult.Value(v)    => v
      case DecodeResult.Error(_, e) => throw e
      case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
    }

  private def throwErrorExceptionMsg[I, E, O, R](endpoint: PublicEndpoint[I, E, O, R], i: I, e: E, r: Request[_, _]): String =
    s"Endpoint ${endpoint.show} returned error: $e, inputs: $i. Request: ${r.showBasic}."

  private def throwErrorExceptionMsg[A, I, E, O, R](endpoint: Endpoint[A, I, E, O, R], a: A, i: I, e: E, r: Request[_, _]): String =
    s"Endpoint ${endpoint.show} returned error: $e, for security inputs: $a, inputs: $i. Request: ${r.showBasic}."
}

object SttpClientInterpreter {
  def apply(clientOptions: SttpClientOptions = SttpClientOptions.default): SttpClientInterpreter = {
    new SttpClientInterpreter {
      override def sttpClientOptions: SttpClientOptions = clientOptions
    }
  }
}
