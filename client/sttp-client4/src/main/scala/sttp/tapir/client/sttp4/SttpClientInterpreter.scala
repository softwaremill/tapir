package sttp.tapir.client.sttp4

import sttp.client4.{Backend, Request}
import sttp.model.Uri
import sttp.tapir.client.sttp4._
import sttp.tapir.{DecodeResult, Endpoint, PublicEndpoint}

trait SttpClientInterpreter {
  def sttpClientOptions: SttpClientOptions = SttpClientOptions.default

  // public

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result of decoding the response (error
    * or success value) is returned.
    */
  def toClient[F[_], I, E, O](
      e: PublicEndpoint[I, E, O, Any],
      baseUri: Option[Uri],
      backend: Backend[F]
  ): I => F[DecodeResult[Either[E, O]]] = { (i: I) =>
    val req = toRequest(e, baseUri)
    backend.monad.map(req(i).send(backend))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result (error or success value) is
    * returned. If decoding the result fails, a failed effect is returned instead.
    */
  def toClientThrowDecodeFailures[F[_], I, E, O](
      e: PublicEndpoint[I, E, O, Any],
      baseUri: Option[Uri],
      backend: Backend[F]
  ): I => F[Either[E, O]] = {
    val req = toRequestThrowDecodeFailures(e, baseUri)
    (i: I) => backend.monad.map(req(i).send(backend))(_.body)
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The request is sent using the given backend, and the result (success value) is returned. If
    * decoding the result fails, or if the response corresponds to an error value, a failed effect is returned instead.
    */
  def toClientThrowErrors[F[_], I, E, O](
      e: PublicEndpoint[I, E, O, Any],
      baseUri: Option[Uri],
      backend: Backend[F]
  ): I => F[O] = {
    val req = toRequestThrowErrors(e, baseUri)
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
  def toRequest[I, E, O](
      e: PublicEndpoint[I, E, O, Any],
      baseUri: Option[Uri]
  ): I => Request[DecodeResult[Either[E, O]]] = {
    new EndpointToSttpClient(sttpClientOptions).toSttpRequest(e, baseUri).apply(())
  }

  /** Interprets the public endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The result of the function is a description of a request, which can be sent using any sttp
    * backend. The response will then contain the decoded error or success values (note that this can be the body enriched with data from
    * headers/status code), or will be a failed effect, when response parsing fails.
    */
  def toRequestThrowDecodeFailures[I, E, O](e: PublicEndpoint[I, E, O, Any], baseUri: Option[Uri]): I => Request[Either[E, O]] =
    i => new EndpointToSttpClient(sttpClientOptions).toSttpRequest(e, baseUri).apply(()).apply(i).mapResponse(throwDecodeFailures)

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
  def toRequestThrowErrors[I, E, O](e: PublicEndpoint[I, E, O, Any], baseUri: Option[Uri]): I => Request[O] =
    i =>
      new EndpointToSttpClient(sttpClientOptions)
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
  def toSecureClient[F[_], A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      baseUri: Option[Uri],
      backend: Backend[F]
  ): A => I => F[DecodeResult[Either[E, O]]] = {
    val req = toSecureRequest(e, baseUri)
    (a: A) => (i: I) => backend.monad.map(req(a)(i).send(backend))(_.body)
  }

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The request is sent using the given backend, and the result (error or
    * success value) is returned. If decoding the result fails, a failed effect is returned instead.
    */
  def toSecureClientThrowDecodeFailures[F[_], A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      baseUri: Option[Uri],
      backend: Backend[F]
  ): A => I => F[Either[E, O]] = {
    val req = toSecureRequestThrowDecodeFailures(e, baseUri)
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
  def toSecureClientThrowErrors[F[_], A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      baseUri: Option[Uri],
      backend: Backend[F]
  ): A => I => F[O] = {
    val req = toSecureRequestThrowErrors(e, baseUri)
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
  def toSecureRequest[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      baseUri: Option[Uri]
  ): A => I => Request[DecodeResult[Either[E, O]]] =
    new EndpointToSttpClient(sttpClientOptions).toSttpRequest(e, baseUri)

  /** Interprets the secure endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If
    * `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's security and regular input parameters (given as tuples), will encode them to
    * appropriate request parameters: path, query, headers and body. The result of the function is a description of a request, which can be
    * sent using any sttp backend. The response will then contain the decoded error or success values (note that this can be the body
    * enriched with data from headers/status code), or will be a failed effect, when response parsing fails.
    */
  def toSecureRequestThrowDecodeFailures[A, I, E, O](
      e: Endpoint[A, I, E, O, Any],
      baseUri: Option[Uri]
  ): A => I => Request[Either[E, O]] =
    a => i => new EndpointToSttpClient(sttpClientOptions).toSttpRequest(e, baseUri).apply(a).apply(i).mapResponse(throwDecodeFailures)

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
  def toSecureRequestThrowErrors[A, I, E, O](e: Endpoint[A, I, E, O, Any], baseUri: Option[Uri]): A => I => Request[O] =
    a =>
      i =>
        new EndpointToSttpClient(sttpClientOptions)
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

object SttpClientInterpreter {
  def apply(clientOptions: SttpClientOptions = SttpClientOptions.default): SttpClientInterpreter = {
    new SttpClientInterpreter {
      override def sttpClientOptions: SttpClientOptions = clientOptions
    }
  }
}
