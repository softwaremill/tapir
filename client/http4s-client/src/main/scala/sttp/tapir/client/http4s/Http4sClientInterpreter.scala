package sttp.tapir.client.http4s

import cats.effect.Async
import org.http4s.{Request, Response, Uri}
import sttp.tapir.{DecodeResult, Endpoint}

abstract class Http4sClientInterpreter[F[_]: Async] {

  def http4sClientOptions: Http4sClientOptions = Http4sClientOptions.default

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If `baseUri` is
    * not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The results of the function are:
    *   - an `org.http4s.Request[F]`, which can be sent using an http4s client, or run against `org.http4s.HttpRoutes[F]`;
    *   - a response parser that extracts the expected entity from the received `org.http4s.Response[F]`.
    */
  def toRequest[I, E, O, R](
      e: Endpoint[I, E, O, R],
      baseUri: Option[Uri]
  ): I => (Request[F], Response[F] => F[DecodeResult[Either[E, O]]]) =
    new EndpointToHttp4sClient(http4sClientOptions).toHttp4sRequest[I, E, O, R, F](e, baseUri)

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target uri. If `baseUri` is
    * not provided, the request will be a relative one.
    *
    * Returns a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them to appropriate request
    * parameters: path, query, headers and body. The results of the function are:
    *   - an `org.http4s.Request[F]`, which can be sent using an http4s client, or run against `org.http4s.HttpRoutes[F]`;
    *   - a response parser that extracts the expected entity from the received `org.http4s.Response[F]`.
    */
  def toRequestUnsafe[I, E, O, R](e: Endpoint[I, E, O, R], baseUri: Option[Uri]): I => (Request[F], Response[F] => F[Either[E, O]]) =
    new EndpointToHttp4sClient(http4sClientOptions).toHttp4sRequestUnsafe[I, E, O, R, F](e, baseUri)
}

object Http4sClientInterpreter {
  def apply[F[_]: Async](clientOptions: Http4sClientOptions = Http4sClientOptions.default): Http4sClientInterpreter[F] =
    new Http4sClientInterpreter[F] {
      override def http4sClientOptions: Http4sClientOptions = clientOptions
    }
}
