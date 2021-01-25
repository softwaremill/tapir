package sttp.tapir.client.sttp

import sttp.capabilities.Effect
import sttp.client3.{Request, SttpBackend}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.{DecodeResult, Endpoint}

trait SttpClientInterpreter extends SttpClientInterpreterExtensions {

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using the given backend,
    * and the result of decoding the response (error or success value) is returned.
    */
  def toClient[F[_], I, E, O, R](e: Endpoint[I, E, O, R with Effect[F]], baseUri: Option[Uri], backend: SttpBackend[F, R])(implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R]
  ): I => F[DecodeResult[Either[E, O]]] = {
    implicit val monad: MonadError[F] = backend.responseMonad
    val req = toRequest[F, I, E, O, R](e, baseUri)
    (i: I) => req(i).flatMap(backend.send).flatMap(_.body)
  }

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using the given backend,
    * and the result (error or success value) is returned. If decoding the result fails, a failed effect is returned
    * instead.
    */
  def toClientThrowDecodeFailures[F[_], I, E, O, R](
      e: Endpoint[I, E, O, R with Effect[F]],
      baseUri: Option[Uri],
      backend: SttpBackend[F, R]
  )(implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R]
  ): I => F[Either[E, O]] = {
    implicit val monad: MonadError[F] = backend.responseMonad
    val req = toRequestThrowDecodeFailures[F, I, E, O, R](e, baseUri)
    (i: I) => req(i).flatMap(backend.send).flatMap(_.body)
  }

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The request is sent using the given backend,
    * and the result (success value) is returned. If decoding the result fails, or if the response corresponds to an
    * error value, a failed effect is returned instead.
    */
  def toClientThrowErrors[F[_], I, E, O, R](e: Endpoint[I, E, O, R with Effect[F]], baseUri: Option[Uri], backend: SttpBackend[F, R])(
      implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R]
  ): I => F[O] = {
    implicit val monad: MonadError[F] = backend.responseMonad
    val req = toRequestThrowErrors[F, I, E, O, R](e, baseUri)
    (i: I) => req(i).flatMap(backend.send).flatMap(_.body)
  }

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The result of the function is a description
    * of a request, which can be sent using any sttp backend. The response will then contain the decoded error or
    * success values (note that this can be the body enriched with data from headers/status code).
    */
  def toRequest[F[_], I, E, O, R](e: Endpoint[I, E, O, R with Effect[F]], baseUri: Option[Uri])(implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R],
      monad: MonadError[F]
  ): I => F[Request[F[DecodeResult[Either[E, O]]], R]] =
    new EndpointToSttpClient(clientOptions, wsToPipe).toSttpRequest(e, baseUri)

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The result of the function is a description
    * of a request, which can be sent using any sttp backend. The response will then contain the decoded error or
    * success values (note that this can be the body enriched with data from headers/status code), or will be a failed
    * effect, when response parsing fails.
    */
  def toRequestThrowDecodeFailures[F[_], I, E, O, R](e: Endpoint[I, E, O, R with Effect[F]], baseUri: Option[Uri])(implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R],
      monad: MonadError[F]
  ): I => F[Request[F[Either[E, O]], R]] =
    i =>
      new EndpointToSttpClient(clientOptions, wsToPipe)
        .toSttpRequest(e, baseUri)
        .apply(i)
        .map(_.mapResponse(fdr => throwDecodeFailures(fdr)))

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri. If `baseUri` is not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The result of the function is a description
    * of a request, which can be sent using any sttp backend. The response will then contain the decoded success values
    * (note that this can be the body enriched with data from headers/status code), or will be a failed
    * effect, when response parsing fails or if the result is an error.
    *
    * @throws IllegalArgumentException when response parsing fails
    */
  def toRequestThrowErrors[F[_], I, E, O, R](e: Endpoint[I, E, O, R with Effect[F]], baseUri: Option[Uri])(implicit
      clientOptions: SttpClientOptions,
      wsToPipe: WebSocketToPipe[R],
      monad: MonadError[F]
  ): I => F[Request[F[O], R]] =
    i =>
      new EndpointToSttpClient(clientOptions, wsToPipe)
        .toSttpRequest(e, baseUri)
        .apply(i)
        .map(
          _.mapResponse(fdr => throwDecodeFailures(fdr))
            .mapResponse(fe =>
              fe.map {
                case Left(t: Throwable) => throw new RuntimeException(throwErrorExceptionMsg(e, i, t.asInstanceOf[E]), t)
                case Left(t)            => throw new RuntimeException(throwErrorExceptionMsg(e, i, t))
                case Right(o)           => o
              }
            )
        )

  private def throwDecodeFailures[F[_], T](fdr: F[DecodeResult[T]])(implicit monad: MonadError[F]): F[T] = {
    fdr.map {
      case DecodeResult.Value(v)    => v
      case DecodeResult.Error(_, e) => throw e
      case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
    }
  }

  private def throwErrorExceptionMsg[I, E, O, R](endpoint: Endpoint[I, E, O, R], i: I, e: E): String =
    s"Endpoint ${endpoint.show} returned error: $e, for inputs: $i."
}

object SttpClientInterpreter extends SttpClientInterpreter
